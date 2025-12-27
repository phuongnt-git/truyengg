package com.truyengg.service.comic;

import com.truyengg.domain.enums.AgeRating;
import com.truyengg.domain.enums.ComicProgressStatus;
import com.truyengg.domain.enums.ComicStatus;
import com.truyengg.domain.enums.Gender;
import com.truyengg.model.dto.ChapterImageInfo;
import com.truyengg.model.dto.ChapterInfo;
import com.truyengg.model.dto.ComicInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComicDetectionService {

  public ComicInfo detectComicInfoHtmlBased(String url, Document doc) {
    // Extract source URL (normalize to base comic URL)
    String source = normalizeComicUrl(url);

    // Comic name from h1 or title - try multiple selectors
    String name = "";

    // TruyenQQ specific selectors first
    String[] nameSelectors = {
        "h1.detail-title",
        "h1.txt-primary",
        "h1.title",
        "h1.book-title",
        "article h1",
        "div.detail-info h1",
        "div.book-info h1",
        "div.manga-info h1",
        "div.comic-info h1",
        "h1"
    };

    for (String selector : nameSelectors) {
      Elements h1Elements = doc.select(selector);
      if (!h1Elements.isEmpty()) {
        name = h1Elements.first().text().trim();
        // Clean up common suffixes
        name = name.replaceAll("\\s*[-–]\\s*TruyenQQ.*$", "").trim();
        name = name.replaceAll("\\s*[-–]\\s*Đọc.*$", "").trim();
        if (StringUtils.isNotBlank(name)) {
          log.debug("Found comic name '{}' using selector '{}'", name, selector);
          break;
        }
      }
    }

    // Fallback to title tag
    if (StringUtils.isBlank(name)) {
      String title = doc.title();
      if (StringUtils.isNotBlank(title)) {
        // Clean up title - remove common suffixes
        name = title.replaceAll("\\s*[-–|]\\s*TruyenQQ.*$", "")
            .replaceAll("\\s*[-–|]\\s*Đọc.*$", "")
            .replaceAll("\\s*[-–|]\\s*Truyện.*$", "")
            .trim();
        log.debug("Found comic name '{}' from title tag", name);
      }
    }

    // Last resort: extract from URL slug
    if (StringUtils.isBlank(name)) {
      name = extractNameFromUrl(url);
      log.debug("Extracted comic name '{}' from URL", name);
    }

    // Origin name (alternative name)
    String originName = "";
    Elements originElements = doc.select("div.detail-info, div.info, div.meta");
    for (Element info : originElements) {
      Elements labels = info.select("span.label, span.title, strong");
      for (Element label : labels) {
        String labelText = label.text().toLowerCase();
        if (labelText.contains("tên gốc") || labelText.contains("origin") || labelText.contains("tên khác")) {
          Element nextSibling = label.nextElementSibling();
          if (nextSibling != null) {
            originName = nextSibling.text().trim();
            break;
          }
        }
      }
    }

    // Alternative names (parse from originName or other sources)
    List<String> alternativeNames = parseAlternativeNames(originName);

    // Description/content
    String content = "";
    Elements descElements = doc.select("div.detail-content, div.description, div.summary, div.content, meta[name=description]");
    if (!descElements.isEmpty()) {
      Element desc = descElements.first();
      if ("meta".equals(desc.tagName())) {
        content = desc.attr("content");
      } else {
        content = desc.text().trim();
      }
    }

    // Author
    String author = "";
    Elements authorElements = doc.select("div.detail-info a[href*='author'], div.info a[href*='author'], " +
        "span:contains('Tác giả'), span:contains('Author')");
    if (!authorElements.isEmpty()) {
      author = authorElements.first().text().trim();
    } else {
      // Try to find in info sections
      Elements infoSections = doc.select("div.detail-info, div.info");
      for (Element section : infoSections) {
        String text = section.text();
        if (text.contains("Tác giả") || text.contains("Author")) {
          Pattern pattern = Pattern.compile("(?:Tác giả|Author)[:：]\\s*(.+?)(?:\\n|$)");
          Matcher matcher = pattern.matcher(text);
          if (matcher.find()) {
            author = matcher.group(1).trim();
            break;
          }
        }
      }
    }

    // Thumbnail
    String thumbUrl = "";
    Elements thumbElements = doc.select("div.detail-cover img, div.cover img, img[src*='thumb'], " +
        "meta[property=og:image]");
    if (!thumbElements.isEmpty()) {
      Element thumb = thumbElements.first();
      if ("meta".equals(thumb.tagName())) {
        thumbUrl = thumb.attr("content");
      } else {
        thumbUrl = thumb.attr("src");
        if (thumbUrl.isEmpty()) {
          thumbUrl = thumb.attr("data-src");
        }
      }
      // Make absolute URL if needed
      if (!thumbUrl.isEmpty() && !thumbUrl.startsWith("http")) {
        try {
          var baseUri = new URI(url);
          var resolvedUri = baseUri.resolve(thumbUrl);
          thumbUrl = resolvedUri.toString();
        } catch (URISyntaxException e) {
          log.warn("Failed to make absolute URL for thumbnail: {}", thumbUrl);
        }
      }
    }

    // Progress Status (ONGOING/COMPLETED)
    ComicProgressStatus progressStatus = ComicProgressStatus.ONGOING;
    String statusText = doc.text().toLowerCase();
    if (statusText.contains("hoàn thành") || statusText.contains("completed") ||
        statusText.contains("đã hoàn thành") || statusText.contains("finished")) {
      progressStatus = ComicProgressStatus.COMPLETED;
    }

    // Extract additional fields
    Long likes = extractLikes(doc);
    Long follows = extractFollows(doc);
    Integer totalChapters = extractTotalChapters(doc);
    ZonedDateTime lastChapterUpdatedAt = extractLastChapterUpdatedAt(doc);
    AgeRating ageRating = extractAgeRating(doc);
    Gender gender = extractGender(doc);
    String country = extractCountry(doc);

    // Slug will be generated by SlugService, so we pass empty string here
    return new ComicInfo(
        name,
        "", // slug - will be generated by SlugService
        originName,
        content,
        ComicStatus.PENDING, // Default workflow status
        progressStatus,
        thumbUrl,
        author,
        likes,
        follows,
        totalChapters,
        lastChapterUpdatedAt,
        source,
        alternativeNames,
        ageRating,
        gender,
        country
    );
  }

  public ComicInfo detectComicInfoMimi(String url, Document doc, Object apiResponse) {
    String source = normalizeComicUrl(url);
    String name = "";
    String author = "";
    String thumbUrl = "";
    ComicProgressStatus progressStatus = ComicProgressStatus.ONGOING;

    // Try API response first
    if (apiResponse instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> apiData = (Map<String, Object>) apiResponse;

      if (apiData.containsKey("response") && apiData.get("response") instanceof List) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) apiData.get("response");
        if (!chapters.isEmpty()) {
          Map<String, Object> firstChapter = chapters.get(0);
          if (firstChapter.containsKey("manga_title")) {
            name = String.valueOf(firstChapter.get("manga_title"));
          }
        }
      }
    }

    // Fallback to HTML parsing
    if (doc != null && name.isEmpty()) {
      Elements titleElements = doc.select("h1, title");
      if (!titleElements.isEmpty()) {
        name = titleElements.first().text().trim();
      }
    }

    if (name.isEmpty()) {
      name = "Manga_" + source;
    }

    return new ComicInfo(
        name,
        "", // slug - will be generated by SlugService
        "",
        "",
        ComicStatus.PENDING, // Default workflow status
        progressStatus,
        thumbUrl,
        author,
        0L, // likes
        0L, // follows
        0, // totalChapters
        null, // lastChapterUpdatedAt
        source,
        new ArrayList<>(), // alternativeNames
        AgeRating.ALL, // ageRating
        Gender.BOTH, // gender
        null // country
    );
  }

  public ChapterInfo detectChapterInfo(String url, Document doc, List<String> imageUrls) {
    // Chapter title
    String chapterTitle = "";
    Elements h1Elements = doc.select("h1.detail-title.txt-primary, h1.detail-title, h1");
    if (!h1Elements.isEmpty()) {
      chapterTitle = h1Elements.first().text().trim().replaceAll("\\s+", " ");
    }

    // Chapter name/number from title or URL
    String chapterName = extractChapterNameFromTitleOrUrl(chapterTitle, url);

    // Convert List<String> to List<ChapterImageInfo>
    List<ChapterImageInfo> chapterImages = new ArrayList<>();
    if (imageUrls != null) {
      for (int i = 0; i < imageUrls.size(); i++) {
        chapterImages.add(new ChapterImageInfo(
            null, // id - will be set when saved
            null, // chapterId - will be set when saved
            imageUrls.get(i), // path - initially same as originalUrl, will be updated when downloaded
            imageUrls.get(i), // originalUrl
            i + 1, // imageOrder
            null, // manualOrder
            false, // isDownloaded
            true, // isVisible
            null, // blurhash - will be generated when downloaded
            null, // deletedAt
            null, // createdAt
            null // updatedAt
        ));
      }
    }

    return new ChapterInfo(
        chapterName,
        chapterTitle,
        url, // source
        chapterImages
    );
  }

  public ChapterInfo detectChapterInfoMimi(Object chapterData, List<String> imageUrls) {
    String chapterName = "";
    String chapterTitle = "";
    String source = "";

    if (chapterData instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> chapter = (Map<String, Object>) chapterData;

      if (chapter.containsKey("title")) {
        chapterTitle = String.valueOf(chapter.get("title"));
      }
      if (chapter.containsKey("id")) {
        chapterName = "chapter-" + chapter.get("id");
        source = "https://mimihentai.com/g/" + chapter.get("id");
      }
    }

    if (chapterName.isEmpty()) {
      chapterName = "chapter-1";
    }

    // Convert List<String> to List<ChapterImageInfo>
    List<ChapterImageInfo> chapterImages = new ArrayList<>();
    if (imageUrls != null) {
      for (int i = 0; i < imageUrls.size(); i++) {
        chapterImages.add(new ChapterImageInfo(
            null, // id - will be set when saved
            null, // chapterId - will be set when saved
            imageUrls.get(i), // path - initially same as originalUrl, will be updated when downloaded
            imageUrls.get(i), // originalUrl
            i + 1, // imageOrder
            null, // manualOrder
            false, // isDownloaded
            true, // isVisible
            null, // blurhash - will be generated when downloaded
            null, // deletedAt
            null, // createdAt
            null // updatedAt
        ));
      }
    }

    return new ChapterInfo(
        chapterName,
        chapterTitle,
        source,
        chapterImages
    );
  }

  private String extractChapterNameFromTitleOrUrl(String title, String url) {
    // Try to extract from title first
    if (StringUtils.isNotBlank(title)) {
      Pattern pattern = Pattern.compile("(?i)(?:chapter|chap|chương)\\s*(\\d+)");
      Matcher matcher = pattern.matcher(title);
      if (matcher.find()) {
        return "chapter-" + matcher.group(1);
      }
    }

    // Fallback to URL
    Pattern pattern = Pattern.compile("(?:chapter|chap|chuong)[-_]?(\\d+)");
    Matcher matcher = pattern.matcher(url);
    if (matcher.find()) {
      return "chapter-" + matcher.group(1);
    }

    // Last resort: use a default name
    return "chapter-1";
  }

  private String normalizeComicUrl(String url) {
    try {
      var uri = new URI(url);
      var path = uri.getPath();
      // Remove chapter-specific paths
      path = path.replaceAll("/(?:chapter|chap|chuong)[-_]?\\d+.*$", "");
      return uri.getScheme() + "://" + uri.getHost() + path;
    } catch (Exception e) {
      log.warn("Failed to normalize comic URL: {}", url, e);
      return url;
    }
  }

  private String extractNameFromUrl(String url) {
    try {
      var uri = new URI(url);
      var path = uri.getPath();
      // Remove trailing slash
      if (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      // Get the last path segment (slug)
      var lastSlash = path.lastIndexOf('/');
      var slug = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
      // Convert slug to readable name: ta-co-mot-son-trai -> Ta Co Mot Son Trai
      if (StringUtils.isNotBlank(slug)) {
        var words = slug.split("[-_]");
        var result = new StringBuilder();
        for (var word : words) {
          if (StringUtils.isNotBlank(word)) {
            if (!result.isEmpty()) {
              result.append(" ");
            }
            // Capitalize first letter
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
              result.append(word.substring(1).toLowerCase());
            }
          }
        }
        return result.toString();
      }
    } catch (Exception e) {
      log.warn("Failed to extract name from URL: {}", url, e);
    }
    return "Unknown Comic";
  }

  private List<String> parseAlternativeNames(String text) {
    var names = new ArrayList<String>();
    if (StringUtils.isBlank(text)) {
      return names;
    }
    // Split by common delimiters
    var parts = text.split("[,;|/]");
    for (var part : parts) {
      var trimmed = part.trim();
      if (StringUtils.isNotBlank(trimmed)) {
        names.add(trimmed);
      }
    }
    return names;
  }

  private Long extractLikes(Document doc) {
    try {
      var elements = doc.select("span:contains('Lượt thích'), span:contains('Likes'), " +
          "div:contains('Lượt thích'), div:contains('Likes')");
      for (var element : elements) {
        var text = element.text();
        var pattern = Pattern.compile("(\\d+(?:[,\\.]\\d+)*)");
        var matcher = pattern.matcher(text);
        if (matcher.find()) {
          return Long.parseLong(matcher.group(1).replaceAll("[,\\.]", ""));
        }
      }
    } catch (Exception e) {
      log.debug("Failed to extract likes", e);
    }
    return 0L;
  }

  private Long extractFollows(Document doc) {
    try {
      var elements = doc.select("span:contains('Theo dõi'), span:contains('Follows'), " +
          "div:contains('Theo dõi'), div:contains('Follows')");
      for (var element : elements) {
        var text = element.text();
        var pattern = Pattern.compile("(\\d+(?:[,\\.]\\d+)*)");
        var matcher = pattern.matcher(text);
        if (matcher.find()) {
          return Long.parseLong(matcher.group(1).replaceAll("[,\\.]", ""));
        }
      }
    } catch (Exception e) {
      log.debug("Failed to extract follows", e);
    }
    return 0L;
  }

  private Integer extractTotalChapters(Document doc) {
    try {
      var elements = doc.select("span:contains('Số chương'), span:contains('Chapters'), " +
          "div:contains('Số chương'), div:contains('Chapters')");
      for (var element : elements) {
        var text = element.text();
        var pattern = Pattern.compile("(\\d+)");
        var matcher = pattern.matcher(text);
        if (matcher.find()) {
          return Integer.parseInt(matcher.group(1));
        }
      }
    } catch (Exception e) {
      log.debug("Failed to extract total chapters", e);
    }
    return 0;
  }

  private ZonedDateTime extractLastChapterUpdatedAt(Document doc) {
    // Try to extract last update time from page
    // This is a simplified version - may need adjustment based on actual HTML structure
    // For now, return null and let the system set it when chapters are crawled
    return null;
  }

  private AgeRating extractAgeRating(Document doc) {
    try {
      var text = doc.text().toLowerCase();
      if (text.contains("18+") || text.contains("mature") || text.contains("người lớn")) {
        return AgeRating.MATURE;
      } else if (text.contains("16+")) {
        return AgeRating.SIXTEEN_PLUS;
      } else if (text.contains("13+")) {
        return AgeRating.THIRTEEN_PLUS;
      }
    } catch (Exception e) {
      log.debug("Failed to extract age rating", e);
    }
    return AgeRating.ALL;
  }

  private Gender extractGender(Document doc) {
    try {
      var text = doc.text().toLowerCase();
      if (text.contains("nam") || text.contains("male") || text.contains("shounen")) {
        return Gender.MALE;
      } else if (text.contains("nữ") || text.contains("female") || text.contains("shoujo")) {
        return Gender.FEMALE;
      } else if (text.contains("cả hai") || text.contains("both") || text.contains("seinen") || text.contains("josei")) {
        return Gender.BOTH;
      }
    } catch (Exception e) {
      log.debug("Failed to extract gender", e);
    }
    return Gender.BOTH;
  }

  private String extractCountry(Document doc) {
    try {
      var elements = doc.select("span:contains('Quốc gia'), span:contains('Country'), " +
          "div:contains('Quốc gia'), div:contains('Country')");
      for (var element : elements) {
        var text = element.text();
        var pattern = Pattern.compile("(?:Quốc gia|Country)[:：]\\s*(.+?)(?:\\n|$)");
        var matcher = pattern.matcher(text);
        if (matcher.find()) {
          return matcher.group(1).trim();
        }
      }
    } catch (Exception e) {
      log.debug("Failed to extract country", e);
    }
    return null;
  }
}
