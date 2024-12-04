package searchengine.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import searchengine.config.UserAgents;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.*;

public class SiteIndexer extends RecursiveAction {
    private static final Logger logger = LogManager.getLogger(LinkParser.class);
    private String url;
    private final Site modelSite;
    private static PageRepository repositoryPage;
    private static SiteRepository repositorySite;
    private static LemmaRepository repositoryLemma;
    private static IndexRepository repositoryIndex;
    private static UserAgents userAgents;
    private static boolean isIndexing;


    public SiteIndexer(Site site, PageRepository repositoryPage, SiteRepository repositorySite,
                       LemmaRepository repositoryLemma, IndexRepository repositoryIndex, UserAgents userAgents) {
        modelSite = site;
        url = newUrl(site.getUrl());
        url = !url.endsWith("/") ? (url + '/') : url;
        SiteIndexer.repositoryPage = repositoryPage;
        SiteIndexer.repositorySite = repositorySite;
        SiteIndexer.repositoryLemma = repositoryLemma;
        SiteIndexer.repositoryIndex = repositoryIndex;
        SiteIndexer.userAgents = userAgents;
        startIndexing();
    }

    private SiteIndexer(String url, Site modelSite) {
        this.url = url;
        this.modelSite = modelSite;
    }

    @Override
    protected void compute() {
        if (!isIndexing) return;
        CopyOnWriteArrayList<SiteIndexer> taskList = new CopyOnWriteArrayList<>();
        Connection connection = LinkParser.getConnection(url, userAgents);
        int statusCode = LinkParser.getStatusCode(Objects.requireNonNull(connection));
        if (statusCode >= 400 && statusCode <= 599 && url.equals(modelSite.getUrl())) {
            errorSite(modelSite, url);
            return;
        }
        String content = LinkParser.getContent(connection);
        ConcurrentSkipListSet<String> links = LinkParser.getLinks(connection, modelSite);
        if (repositoryPage.findPageByPathAndSite(getPath(url), modelSite) != null) {
            logger.error("\u001B[31m*** THE PAGE IS ALREADY IN THE DATABASE ***\u001B[0m{}", url);
            return;
        }
        logger.info("\u001B[32m*** {}  -- {} ***\u001B[0m", Thread.currentThread().getName(), url);
        Page page = new Page(modelSite, getPath(url), statusCode, content);
        repositoryPage.save(page);
        if (!isIndexing) {
            modelSite.setStatus(Status.FAILED);
            modelSite.setLastError("Индексация остановлена пользователем");
        }
        modelSite.setStatusTime(new Date());
        repositorySite.save(modelSite);
        new LemmaIndexer(modelSite, page, repositoryLemma, repositoryIndex).run();
        for (String link : links) {
            if (!isIndexing) return;
            SiteIndexer task = new SiteIndexer(link, modelSite);
            task.fork();
            taskList.add(task);
        }
        taskList.forEach(ForkJoinTask::join);
}

    public static void stopIndexing() {
        isIndexing = false;
    }

    public static void startIndexing() {
        isIndexing = true;
    }

    public static boolean isIndexing() {
        return isIndexing;
    }

    public static String getPath(String url) {
        String path = "";
        try {
            path = new URL(url).getPath();
            if (path.length() > 1 && path.charAt(path.length() - 1) == '/')
            {
                path = path.substring(0, path.length() - 1);
            }
        } catch (MalformedURLException e) {
            logger.error("\u001B[31m*** ERROR GETTING THE LINK PATH ***\u001B[0m");
        }
        return path;
    }


    private static void errorSite (Site modelSite, String url) {
        modelSite.setStatus(Status.FAILED);
        modelSite.setLastError("Ошибка индексации: главная страница сайта недоступна");
        modelSite.setStatusTime(new Date());
        repositorySite.save(modelSite);
        logger.error("\u001B[31m*** INDEXING ERROR: THE MAIN PAGE OF THE SITE {} IS UNAVAILABLE ***\u001B[0m", url);
    }

    public static String newUrl(String url) {
        return url.replace("www.", "");
    }

}