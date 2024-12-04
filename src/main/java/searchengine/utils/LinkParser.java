package searchengine.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.UserAgents;
import searchengine.model.Site;
import java.io.IOException;
import java.util.concurrent.ConcurrentSkipListSet;
import static searchengine.utils.SiteIndexer.newUrl;

public class LinkParser {
    private static final Logger logger = LogManager.getLogger(LinkParser.class);


    public static Connection getConnection(String url, UserAgents userAgents) {
        try {
            return Jsoup.connect(url)
                    .userAgent(userAgents.getRandomUser())
                    .referrer(userAgents.getRandomUser())
                    .ignoreHttpErrors(true)
                    .followRedirects(true);
        } catch (Exception e) {
            logger.error("\u001B[31m*** ERROR GET CONNECTION ***\u001B[0m"  + "\n" + e);
            //throw new RuntimeException();
        }
        return null;
    }

    public static int getStatusCode(Connection connection)  {
        try {
            return connection.execute().statusCode();
        } catch (IOException e) {
            logger.error("\u001B[31m*** ERROR RECEIVING THE STATUS CODE ***\u001B[0m" + "\n" + e);
            //throw new RuntimeException(e);
        }
        return 0;
    }

    public static String getContent(Connection connection) {
        if (getStatusCode(connection) == 200) {
            try {
                return connection.get().html();
            } catch (IOException e) {
                logger.error("\u001B[31m*** ERROR RECEIVING PAGE CONTENT ***\u001B[0m"  + "\n" + e);
            }
        }
        return "";
    }

    public static ConcurrentSkipListSet<String> getLinks(Connection connection, Site modelSite)  {
        ConcurrentSkipListSet<String> links = new ConcurrentSkipListSet<>();
        Document document;
        try {
            document = connection.get();
            Elements elements = document.select("body").select("a");
            for (Element element : elements) {
                String link = element.absUrl("href");
                if (isLink(link) &&
                        !isFile(link) &&
                        !link.isEmpty() &&
                        !containsPagination(link) &&
                        newUrl(link).contains(newUrl(modelSite.getUrl()))) {
                    link = link.split("#")[0];
                    links.add(link);
                }
            }
        } catch (IOException e) {
            logger.error("\u001B[31m*** ERROR GETTING PAGE LINKS ***\u001B[0m"  + "\n" + e);
            //throw new RuntimeException(e);
        }
        return links;
    }

    private static boolean isLink(String link) {
        String regex = "^(https?|ftp)://[-\\w+&@#/%?=~_|!:,.;]*[-\\w+&@#/%=~_|]";
        return link.matches(regex);
    }

    private static boolean isFile(String link) {
        link = link.toLowerCase();
        return link.contains(".jpg")
                || link.contains(".jpeg")
                || link.contains(".png")
                || link.contains(".gif")
                || link.contains(".webp")
                || link.contains(".pdf")
                || link.contains(".eps")
                || link.contains(".xlsx")
                || link.contains(".doc")
                || link.contains(".pptx")
                || link.contains(".docx")
                || link.contains("?_ga")
                || link.contains(".zip")
                || link.contains(".sql");
    }

    private static boolean containsPagination(String link) {
        link = link.toLowerCase();
        return link.contains("sort")
                || link.contains("page")
                || link.contains("size")
                || link.contains("product_info");

    }

}