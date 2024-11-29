package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final Logger logger = LogManager.getLogger(StatisticsServiceImpl.class);

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        List<DetailedStatisticsItem> detailedList = new ArrayList<>();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);
        for (Site site : sites.getSites()) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            if (siteRepository.findSiteByUrl(site.getUrl()) != null) {
                searchengine.model.Site modelSite = siteRepository.findSiteByUrl(site.getUrl());
                Integer pagesCount = pageRepository.countBySite(modelSite);
                pagesCount = pagesCount == null ? 0 : pagesCount;
                int lemmasCount = lemmaRepository.countBySite(modelSite);
                item.setStatus(String.valueOf(modelSite.getStatus()));
                item.setPages(pagesCount);
                item.setLemmas(lemmasCount);
                item.setError(modelSite.getLastError());
                item.setStatusTime(modelSite.getStatusTime().getTime());
                detailedList.add(item);
                total.setPages(total.getPages() + pagesCount);
                total.setLemmas(total.getLemmas() + lemmasCount);
            } else {
                logger.error("\u001B[32m*** INDEXED SITES WERE NOT FOUND ***\u001B[0m");
            }
        }
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailedList);
        response.setStatistics(data);
        response.setResult(true);
        logger.info("\u001B[32m*** GENERAL STATISTIC ON ALL SITES HAVE BEEN COLLECTED SUCCESSFULLY ***\u001B[0m");
        return response;
    }

}
