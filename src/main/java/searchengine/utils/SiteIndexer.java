package searchengine.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.*;

public class SiteIndexer extends RecursiveAction {
    private static final Logger logger = LogManager.getLogger(LinkParser.class);
    private String url;
    private final Site modelSite;
    @Autowired
    private static PageRepository repositoryPage;
    @Autowired
    private static SiteRepository repositorySite;
    @Autowired
    private static LemmaRepository repositoryLemma;
    @Autowired
    private static IndexRepository repositoryIndex;
    private static UserAgents userAgents;
    private static boolean isIndexing;


    public SiteIndexer(Site site, PageRepository repositoryPage, SiteRepository repositorySite,
                       LemmaRepository repositoryLemma, IndexRepository repositoryIndex, UserAgents userAgents) {
        modelSite = site;
        url = site.getUrl().replace("www.", "");
        url = !url.endsWith("/") ? (url + '/') : url; //для обрезания path после / в дальнейшем
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
        ConcurrentSkipListSet<String> links;
        Connection connection = LinkParser.getConnection(url, userAgents);
        int statusCode = LinkParser.getStatusCode(connection);
        if (statusCode >= 400 && statusCode <= 599 && url.equals(modelSite.getUrl()))
        {
            modelSite.setStatus(Status.FAILED);
            modelSite.setLastError("Ошибка индексации: главная страница сайта недоступна");
            modelSite.setStatusTime(new Date());
            repositorySite.save(modelSite);
            logger.error("\u001B[31m*** INDEXING ERROR: THE MAIN PAGE OF THE SITE {} IS UNAVAILABLE ***\u001B[0m", url);
            return;
        }
        String content = LinkParser.getContent(connection);
        links = LinkParser.getLinks(connection);
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
            if (link.contains(url)) {
                SiteIndexer task = new SiteIndexer(link, modelSite);
                task.fork();
                taskList.add(task);
            }
            taskList.forEach(ForkJoinTask::join);
        }
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
        String path;
        try {
            path = new URL(url).getPath();
            if (path.length() > 1 && path.charAt(path.length() - 1) == '/')
            {
                path = path.substring(0, path.length() - 1);
            }
        } catch (MalformedURLException e) {
            logger.error("\u001B[31m*** ERROR GETTING THE LINK PATH ***\u001B[0m");
            throw new RuntimeException(e);
        }
        return path;
    }

}