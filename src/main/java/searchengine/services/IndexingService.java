package searchengine.services;

import searchengine.dto.index.IndexingResponse;

public interface IndexingService {

    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
    IndexingResponse indexPage(String url);

}
