package de.digitalcollections.streaming.euphoria.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import de.digitalcollections.commons.file.business.api.FileResourceService;
import de.digitalcollections.model.api.identifiable.resource.FileResource;
import de.digitalcollections.model.api.identifiable.resource.MimeType;
import de.digitalcollections.model.api.identifiable.resource.exceptions.ResourceIOException;
import de.digitalcollections.model.api.identifiable.resource.exceptions.ResourceNotFoundException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Based on <a
 * href="https://github.com/omnifaces/omnifaces/blob/develop/src/main/java/org/omnifaces/servlet/FileServlet.java">Omnifaces
 * FileServlet.java</a> (Apache License 2.0)
 *
 * <p>This implementation properly deals with <code>ETag</code>, <code>If-None-Match</code> and
 * <code>If-Modified-Since</code> caching requests, hereby improving browser caching. This servlet
 * also properly deals with <code>Range</code> and <code>If-Range</code> ranging requests (<a
 * href="https://tools.ietf.org/html/rfc7233">RFC7233</a>), which is required by most media players
 * for proper audio/video streaming, and by webbrowsers and for a proper resume of an paused
 * download, and by download accelerators to be able to request smaller parts simultaneously. This
 * implementaion is ideal when you have large files like media files placed outside the web
 * application and you can't use the default servlet.
 *
 * <ul>
 *   <li><a href="http://stackoverflow.com/q/13588149/157882">How to stream audio/video files such
 *       as MP3, MP4, AVI, etc using a Servlet</a>
 *   <li><a href="http://stackoverflow.com/a/29991447/157882">Abstract template for a static
 *       resource servlet</a>
 * </ul>
 */
@RestController
public class StreamingController {

  private static final String CONTENT_DISPOSITION_HEADER =
      "%s;filename=\"%2$s\"; filename*=UTF-8''%2$s";
  private static final Long DEFAULT_EXPIRE_TIME_IN_SECONDS = TimeUnit.DAYS.toSeconds(30);
  private static final int DEFAULT_STREAM_BUFFER_SIZE = 10240; // 10 kB;
  private static final String ERROR_UNSUPPORTED_ENCODING =
      "UTF-8 is apparently not supported on this platform.";
  private static final Logger LOGGER = LoggerFactory.getLogger(StreamingController.class);
  private static final String MULTIPART_BOUNDARY = "MULTIPART_BYTERANGES";
  private static final long ONE_SECOND_IN_MILLIS = TimeUnit.SECONDS.toMillis(1);
  private static final Pattern RANGE_PATTERN =
      Pattern.compile("^bytes=[0-9]*-[0-9]*(,[0-9]*-[0-9]*)*$");

  @Autowired FileResourceService fileResourceService;

  /**
   * Returns true if the given accept header accepts the given value.
   *
   * @param acceptHeader The accept header.
   * @param toAccept The value to be accepted.
   * @return True if the given accept header accepts the given value.
   */
  private static boolean accepts(String acceptHeader, String toAccept) {
    String[] acceptValues = acceptHeader.split("\\s*(,|;)\\s*");
    Arrays.sort(acceptValues);
    return Arrays.binarySearch(acceptValues, toAccept) > -1
        || Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1
        || Arrays.binarySearch(acceptValues, "*/*") > -1;
  }

  /**
   * Close the given resource.
   *
   * @param resource The resource to be closed.
   */
  private static void close(Closeable resource) {
    if (resource != null) {
      try {
        resource.close();
      } catch (IOException ignore) {
        // Ignore IOException. If you want to handle this anyway, it might be useful to know
        // that this will generally only be thrown when the client aborted the request.
      }
    }
  }

  /**
   * Returns true if the given match header matches the given value.
   *
   * @param matchHeader The match header.
   * @param toMatch The value to be matched.
   * @return True if the given match header matches the given value.
   */
  private static boolean matches(String matchHeader, String toMatch) {
    String[] matchValues = matchHeader.split("\\s*,\\s*");
    Arrays.sort(matchValues);
    return Arrays.binarySearch(matchValues, toMatch) > -1
        || Arrays.binarySearch(matchValues, "*") > -1;
  }

  /** Returns true if the given modified header is older than the given last modified value. */
  private static boolean modified(long modifiedHeader, long lastModified) {
    return (modifiedHeader + ONE_SECOND_IN_MILLIS
        <= lastModified); // That second is because the header is in seconds, not millis.
  }

  /**
   * Returns a substring of the given string value from the given begin index to the given end index
   * as a long. If the substring is empty, then -1 will be returned
   *
   * @param value The string value to return a substring as long for.
   * @param beginIndex The begin index of the substring to be returned as long.
   * @param endIndex The end index of the substring to be returned as long.
   * @return A substring of the given string value as long or -1 if substring is empty.
   */
  private static long sublong(String value, int beginIndex, int endIndex) {
    String substring = value.substring(beginIndex, endIndex);
    return (substring.length() > 0) ? Long.parseLong(substring) : -1;
  }

  /**
   * Copy the given byte range of the given input to the given output.
   *
   * @param input The input to copy the given range to the given output for.
   * @param output The output to copy the given range from the given input for.
   * @param start Start of the byte range.
   * @param inputSize the length of the entire resource.
   * @param length Length of the byte range.
   * @throws IOException If something fails at I/O level.
   */
  @SuppressFBWarnings(
      value = "SR_NOT_CHECKED",
      justification =
          "Return check of input.skip() is done later in while-loop and used as terminating loop")
  private void copy(InputStream input, OutputStream output, long inputSize, long start, long length)
      throws IOException {
    byte[] buffer = new byte[DEFAULT_STREAM_BUFFER_SIZE];
    int read;

    if (inputSize == length) {
      LOGGER.debug(
          "*** Response: writing FULL RANGE (from byte {} to byte {} = {} kB of total {} kB)",
          start,
          (start + length - 1),
          length / 1024,
          inputSize / 1024);
      stream(input, output);
    } else {
      LOGGER.debug(
          "*** Response: writing partial range (from byte {} to byte {} = {} kB of total {} kB)",
          start,
          (start + length - 1),
          length / 1024,
          inputSize / 1024);
      input.skip(start);
      long toRead = length;

      while ((read = input.read(buffer)) > 0) {
        if ((toRead -= read) > 0) {
          output.write(buffer, 0, read);
          //          output.flush();
        } else {
          output.write(buffer, 0, (int) toRead + read);
          //          output.flush();
          break;
        }
      }
    }
  }

  /**
   * URI-encode the given string using UTF-8. URIs (paths and filenames) have different encoding
   * rules as compared to URL query string parameters. {@link URLEncoder} is actually only for www
   * (HTML) form based query string parameter values (as used when a webbrowser submits a HTML
   * form). URI encoding has a lot in common with URL encoding, but the space has to be %20 and some
   * chars doesn't necessarily need to be encoded.
   *
   * @param string The string to be URI-encoded using UTF-8.
   * @return The given string, URI-encoded using UTF-8, or <code>null</code> if <code>null</code>
   *     was given.
   * @throws UnsupportedOperationException When this platform does not support UTF-8.
   * @since 2.4
   */
  private String encodeURI(String string) {
    if (string == null) {
      return null;
    }

    return encodeURL(string)
        .replace("+", "%20")
        .replace("%21", "!")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%7E", "~");
  }

  /**
   * URL-encode the given string using UTF-8.
   *
   * @param string The string to be URL-encoded using UTF-8.
   * @return The given string, URL-encoded using UTF-8, or <code>null</code> if <code>null</code>
   *     was given.
   * @throws UnsupportedOperationException When this platform does not support UTF-8.
   * @since 1.4
   */
  private String encodeURL(String string) {
    if (string == null) {
      return null;
    }

    try {
      return URLEncoder.encode(string, UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new UnsupportedOperationException(ERROR_UNSUPPORTED_ENCODING, e);
    }
  }

  @RequestMapping(value = "/stream/{id}/default.{extension}", method = RequestMethod.HEAD)
  public void getHead(
      @PathVariable String id,
      @PathVariable String extension,
      HttpServletRequest request,
      HttpServletResponse response)
      throws Exception {
    LOGGER.info("HEAD request!");
    respond(id, extension, request, response, true);
  }

  /**
   * Set the no-cache headers. The following headers will be set:
   *
   * <ul>
   *   <li><code>Cache-Control: no-cache,no-store,must-revalidate</code>
   *   <li><code>Expires: [expiration date of 0]</code>
   *   <li><code>Pragma: no-cache</code>
   * </ul>
   *
   * Set the no-cache headers.
   *
   * @param response The HTTP servlet response to set the headers on.
   */
  private void setNoCacheHeaders(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-cache,no-store,must-revalidate");
    response.setDateHeader("Expires", 0);
    response.setHeader("Pragma", "no-cache"); // Backwards compatibility for HTTP 1.0.
  }

  /**
   * Get requested ranges. If this is null, then we must return 416. If this is empty, then we must
   * return full file.
   */
  private List<Range> getRanges(HttpServletRequest request, ResourceInfo resourceInfo) {
    List<Range> ranges = new ArrayList<>(1);
    String rangeHeader = request.getHeader("Range");

    if (rangeHeader == null) {
      return ranges;
    } else if (!RANGE_PATTERN.matcher(rangeHeader).matches()) {
      return null; // Syntax error.
    }

    String ifRange = request.getHeader("If-Range");

    if (ifRange != null && !ifRange.equals(resourceInfo.eTag)) {
      try {
        long ifRangeTime = request.getDateHeader("If-Range");
        if (ifRangeTime != -1 && modified(ifRangeTime, resourceInfo.lastModified)) {
          return ranges;
        }
      } catch (IllegalArgumentException ex) {
        return ranges;
      }
    }

    for (String rangeHeaderPart : rangeHeader.split("=")[1].split(",")) {
      Range range = parseRange(rangeHeaderPart, resourceInfo.length);
      if (range == null) {
        return null; // Logic error.
      }
      ranges.add(range);
    }

    return ranges;
  }

  private FileResource getResource(String id, String extension)
      throws ResourceIOException, ResourceNotFoundException {
    FileResource resource = fileResourceService.find(id, extension);
    return resource;
  }

  @RequestMapping(value = "/stream/{id}/default.{extension}", method = RequestMethod.GET)
  public void getStream(
      @PathVariable String id,
      @PathVariable String extension,
      HttpServletRequest request,
      HttpServletResponse response)
      throws Exception {
    LOGGER.info("Stream for resource {}.{} requested.", id, extension);
    respond(id, extension, request, response, false);
  }

  /**
   * Returns <code>true</code> if we must force a "Save As" dialog based on the given HTTP servlet
   * request and content type as obtained from {@link #getContentType(HttpServletRequest, File)}.
   *
   * <p>The default implementation will return <code>true</code> if the content type does
   * <strong>not</strong> start with <code>text</code> or <code>image</code>, and the <code>Accept
   * </code> request header is either <code>null</code> or does not match the given content type.
   *
   * @param request The involved HTTP servlet request.
   * @param contentType The content type of the involved file.
   * @return <code>true</code> if we must force a "Save As" dialog based on the given HTTP servlet
   *     request and content type.
   */
  private boolean isAttachment(HttpServletRequest request, String contentType) {
    String accept = request.getHeader("Accept");
    return !startsWithOneOf(contentType, "text", "image")
        && (accept == null || !accepts(accept, contentType));
  }

  private void logRequestHeaders(HttpServletRequest request) {
    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();
      Enumeration<String> headers = request.getHeaders(headerName);
      while (headers.hasMoreElements()) {
        String header = headers.nextElement();
        LOGGER.debug("request header: {} = {}", headerName, header);
      }
    }
  }

  /**
   *
   *
   * <ul>
   *   <li>Request-Header "If-None-Match" should contain "*" or ETag.
   *   <li>If-Modified-Since header should be greater than LastModified. This header is ignored if
   *       any If-None-Match header is specified.
   * </ul>
   */
  private boolean notModified(HttpServletRequest request, ResourceInfo resourceInfo) {
    String noMatch = request.getHeader("If-None-Match");
    long modified = request.getDateHeader("If-Modified-Since");
    return (noMatch != null)
        ? matches(noMatch, resourceInfo.eTag)
        : (modified != -1 && !modified(modified, resourceInfo.lastModified));
  }

  /**
   * Parse range header part. Returns null if there's a logic error (i.e. start after end).
   *
   * <p>The first-byte-pos value in a byte-range-spec gives the byte-offset of the first byte in a
   * range. The last-byte-pos value gives the byte-offset of the last byte in the range; that is,
   * the byte positions specified are inclusive. Byte offsets start at zero.
   *
   * <p>Examples of byte-ranges-specifier values: The first 500 bytes (byte offsets 0-499,
   * inclusive): bytes=0-499 The second 500 bytes (byte offsets 500-999, inclusive): bytes=500-999
   *
   * <p>A byte-range-spec is invalid if the last-byte-pos value is present and less than the
   * first-byte-pos.
   */
  private Range parseRange(String range, long length) {
    long start = sublong(range, 0, range.indexOf('-'));
    long end = sublong(range, range.indexOf('-') + 1, range.length());

    if (start == -1) {
      /*
      A client can request the last N bytes of the selected representation using a suffix-byte-range-spec.

      Examples, assuming a representation of length 10000:
      The final 500 bytes (byte offsets 9500-9999, inclusive):  bytes=-500
      Or:  bytes=9500-
       */
      start = length - end;
      end = length - 1;
    } else if (end == -1 || end > length - 1) {
      /*
      A client can limit the number of bytes requested without knowing the size of the selected representation.
      If the last-byte-pos value is absent, or if the value is greater than or equal to the current length of the
      representation data, the byte range is interpreted as the remainder of the representation (i.e., the server
      replaces the value of last-byte-pos with a value that is one less than the current length of the selected
      representation).

      Example:
      All bytes: bytes=0-
       */
      end = length - 1;
    }

    if (start > end) {
      return null; // Logic error.
    }

    return new Range(start, end);
  }

  /**
   * Validate request headers for resume.
   *
   * <ul>
   *   <li>"If-Match" header should contain "*" or ETag.
   *   <li>"If-Unmodified-Since" header should be greater than LastModified.
   * </ul>
   */
  private boolean preconditionFailed(HttpServletRequest request, ResourceInfo resourceInfo) {
    String match = request.getHeader("If-Match");
    long unmodified = request.getDateHeader("If-Unmodified-Since");
    return (match != null)
        ? !matches(match, resourceInfo.eTag)
        : (unmodified != -1 && modified(unmodified, resourceInfo.lastModified));
  }

  /**
   * Create response for the request.
   *
   * @param id The id of requested content.
   * @param extension The (target) file extension/format of requested content.
   * @param request The request to be responded to.
   * @param response The response to the request.
   * @param head "true" if response body should be written (GET) or "false" if not (HEAD).
   * @throws IOException If something fails at I/O level.
   */
  private void respond(
      String id,
      String extension,
      HttpServletRequest request,
      HttpServletResponse response,
      boolean head)
      throws ResourceNotFoundException, IOException {
    logRequestHeaders(request);

    response.reset();

    // try to get access to resource
    FileResource resource;
    try {
      resource = getResource(id, extension);
    } catch (ResourceIOException ex) {
      LOGGER.warn(
          "*** Response {}: Error referencing streaming resource with id {} and extension {}",
          HttpServletResponse.SC_NOT_FOUND,
          id,
          extension);
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    // get resource metadata
    ResourceInfo resourceInfo = new ResourceInfo(id, resource);
    if (resourceInfo.length <= 0) {
      LOGGER.warn(
          "*** Response {}: Error streaming resource with id {} and extension {}: not found/no size",
          HttpServletResponse.SC_NOT_FOUND,
          id,
          extension);
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    if (preconditionFailed(request, resourceInfo)) {
      LOGGER.warn(
          "*** Response {}: Precondition If-Match/If-Unmodified-Since failed for resource with id {} and extension {}.",
          HttpServletResponse.SC_PRECONDITION_FAILED,
          id,
          extension);
      response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
      return;
    }

    setCacheHeaders(response, resourceInfo);

    if (notModified(request, resourceInfo)) {
      LOGGER.debug(
          "*** Response {}: 'Not modified'-response for resource with id {} and extension {}.",
          HttpServletResponse.SC_NOT_MODIFIED,
          id,
          extension);
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      return;
    }

    List<Range> ranges = getRanges(request, resourceInfo);

    if (ranges == null) {
      response.setHeader("Content-Range", "bytes */" + resourceInfo.length);
      LOGGER.warn(
          "Response {}: Header Range for resource with id {} and extension {} not satisfiable",
          HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE,
          id,
          extension);
      response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
      return;
    }

    if (!ranges.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
    } else {
      ranges.add(new Range(0, resourceInfo.length - 1)); // Full content.
    }

    String contentType = setContentHeaders(request, response, resourceInfo, ranges);
    boolean acceptsGzip = false;
    // If content type is text, then determine whether GZIP content encoding is supported by
    // the browser and expand content type with the one and right character encoding.
    if (contentType.startsWith("text")) {
      String acceptEncoding = request.getHeader("Accept-Encoding");
      acceptsGzip = acceptEncoding != null && accepts(acceptEncoding, "gzip");
      contentType += ";charset=UTF-8";
    }

    if (head) {
      return;
    }

    writeContent(response, resource, resourceInfo, ranges, contentType, acceptsGzip);
    LOGGER.debug("*** RESPONSE FINISHED ***");
  }

  /**
   * Set the cache headers. If the <code>expires</code> argument is larger than 0 seconds, then the
   * following headers will be set:
   *
   * <ul>
   *   <li><code>Cache-Control: public,max-age=[expiration time in seconds],must-revalidate</code>
   *   <li><code>Expires: [expiration date of now plus expiration time in seconds]</code>
   * </ul>
   *
   * <p>Else the method will delegate to {@link #setNoCacheHeaders(HttpServletResponse)}.
   *
   * @param response The HTTP servlet response to set the headers on.
   * @param expires The expire time in seconds (not milliseconds!).
   */
  private void setCacheHeaders(HttpServletResponse response, long expires) {
    if (expires > 0) {
      response.setHeader("Cache-Control", "public,max-age=" + expires + ",must-revalidate");
      response.setDateHeader("Expires", System.currentTimeMillis() + SECONDS.toMillis(expires));
      response.setHeader(
          "Pragma", ""); // Explicitly set pragma to prevent container from overriding it.
    } else {
      setNoCacheHeaders(response);
    }
  }

  /** Caching, see https://tools.ietf.org/html/rfc7232#section-3.2 */
  private void setCacheHeaders(HttpServletResponse response, ResourceInfo resourceInfo) {
    setCacheHeaders(response, DEFAULT_EXPIRE_TIME_IN_SECONDS);
    response.setHeader("ETag", resourceInfo.eTag);
    response.setDateHeader("Last-Modified", resourceInfo.lastModified);
  }

  private String setContentHeaders(
      HttpServletRequest request,
      HttpServletResponse response,
      ResourceInfo resourceInfo,
      List<Range> ranges) {
    String contentType = resourceInfo.contentType;
    // If content type is unknown, then set the default value.
    // For all content types, see: http://www.w3schools.com/media/media_mimeref.asp
    // To add new content types, add new mime-mapping entry in web.xml.
    if (contentType == null) {
      contentType = "application/octet-stream";
    }
    String disposition = isAttachment(request, contentType) ? "attachment" : "inline";
    String filename = encodeURI(resourceInfo.fileName);
    response.setHeader(
        "Content-Disposition", String.format(CONTENT_DISPOSITION_HEADER, disposition, filename));
    response.setHeader("Accept-Ranges", "bytes");

    if (ranges.size() == 1) {
      Range range = ranges.get(0);
      response.setContentType(contentType);
      response.setHeader("Content-Length", String.valueOf(range.length));

      if (response.getStatus() == HttpServletResponse.SC_PARTIAL_CONTENT) {
        response.setHeader(
            "Content-Range", "bytes " + range.start + "-" + range.end + "/" + resourceInfo.length);
      }
    } else {
      response.setContentType("multipart/byteranges; boundary=" + MULTIPART_BOUNDARY);
    }

    return contentType;
  }

  /**
   * Returns <code>true</code> if the given string starts with one of the given prefixes.
   *
   * @param string The object to be checked if it starts with one of the given prefixes.
   * @param prefixes The argument list of prefixes to be checked
   * @return <code>true</code> if the given string starts with one of the given prefixes.
   * @since 1.4
   */
  private boolean startsWithOneOf(String string, String... prefixes) {
    for (String prefix : prefixes) {
      if (string.startsWith(prefix)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Stream the given input to the given output via NIO {@link Channels} and a directly allocated
   * NIO {@link ByteBuffer}. Both the input and output streams will implicitly be closed after
   * streaming, regardless of whether an exception is been thrown or not.
   *
   * @param input The input stream.
   * @param output The output stream.
   * @return The length of the written bytes.
   * @throws IOException When an I/O error occurs.
   */
  @SuppressFBWarnings(
      value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
      justification = ".read() function in while-loop needs boundary check to terminate loop")
  private long stream(InputStream input, OutputStream output) throws IOException {
    try (ReadableByteChannel inputChannel = Channels.newChannel(input);
        WritableByteChannel outputChannel = Channels.newChannel(output)) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(DEFAULT_STREAM_BUFFER_SIZE);
      long size = 0;

      while (inputChannel.read(buffer) != -1) {
        buffer.flip();
        size += outputChannel.write(buffer);
        buffer.clear();
      }

      return size;
    }
  }

  private void writeContent(
      HttpServletResponse response,
      FileResource resource,
      ResourceInfo resourceInfo,
      List<Range> ranges,
      String contentType,
      boolean acceptsGzip)
      throws ResourceNotFoundException, IOException {
    OutputStream output = null;
    InputStream datastream = null;
    BufferedInputStream input = null;
    try {
      output = response.getOutputStream();
      if (acceptsGzip) {
        // The browser accepts GZIP, so GZIP the content.
        response.setHeader("Content-Encoding", "gzip");
        output = new GZIPOutputStream(output, DEFAULT_STREAM_BUFFER_SIZE);
      }
      datastream = fileResourceService.getInputStream(resource);
      // Open streams.
      input = new BufferedInputStream(datastream);

      if (ranges.size() == 1) {
        Range range = ranges.get(0);
        copy(input, output, resourceInfo.length, range.start, range.length);
      } else {
        // Cast back to ServletOutputStream to get the easy println methods.
        ServletOutputStream sos = (ServletOutputStream) output;

        for (Range range : ranges) {
          sos.println();
          sos.println("--" + MULTIPART_BOUNDARY);
          sos.println("Content-Type: " + contentType);
          sos.println(
              "Content-Range: bytes " + range.start + "-" + range.end + "/" + resourceInfo.length);
          copy(input, sos, resourceInfo.length, range.start, range.length);
        }

        sos.println();
        sos.println("--" + MULTIPART_BOUNDARY + "--");
      }
    } finally {
      // Gently close streams.
      close(output);
      close(input);
      if (datastream != null) {
        close(datastream);
      }
    }
  }

  // Inner classes ------------------------------------------------------------------------------
  private static class ResourceInfo {

    private final String contentType;
    private final String eTag;
    private final String fileExtension;
    private final String fileName;
    private final long lastModified;
    private final long length;

    private ResourceInfo(String id, FileResource resource) {
      length = resource.getSizeInBytes();
      fileName = resource.getFilename();
      lastModified = resource.getLastModified().toEpochSecond(ZoneOffset.UTC);
      fileExtension = FilenameUtils.getExtension(fileName);
      contentType = MimeType.fromExtension(fileExtension).getTypeName();
      // unique identifier for resource (with timestamp and size):
      eTag = id + "." + fileExtension + "_" + length + "_" + lastModified;

      LOGGER.info("eTag for requested resource = {}", eTag);
    }
  }

  /** This class represents a byte range. */
  protected static class Range {

    long start;
    long end;
    long length;

    /**
     * Construct a byte range.
     *
     * @param start Start of the byte range.
     * @param end End of the byte range.
     */
    public Range(long start, long end) {
      this.start = start;
      this.end = end;
      this.length = end - start + 1;
    }
  }
}
