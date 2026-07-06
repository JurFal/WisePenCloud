package com.oriole.wisepen.resource.constant;

public interface SearchConstants {

    String RESOURCE_INDEX_NAME = "wisepen_resource_index";
    String MARKET_RESOURCE_INDEX_NAME = "wisepen_market_resource_index";

    String ANALYZER_IK_MAX_WORD = "ik_max_word";
    String ANALYZER_IK_SMART = "ik_smart";

    String[] BOOSTED_SEARCH_FIELDS = {"resourceName^2", "content"};
    String[] MARKET_BOOSTED_SEARCH_FIELDS = {"resourceName^2", "previewContent", "searchContent^0.4"};

    String HIGHLIGHT_PRE_TAG = "<em class=\"wp-highlight\">";
    String HIGHLIGHT_POST_TAG = "</em>";
    int HIGHLIGHT_FRAGMENT_SIZE = 100;
    int HIGHLIGHT_MAX_FRAGMENTS = 3;
    String HIGHLIGHT_FRAGMENT_SEPARATOR = "...";

    String ES_DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS";
}
