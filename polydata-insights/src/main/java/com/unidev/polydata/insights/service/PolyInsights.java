package com.unidev.polydata.insights.service;


import com.unidev.polydata.domain.BasicPoly;
import com.unidev.polydata.insights.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Service for doing operations on insights
 */
@Service
public class PolyInsights {

    private static Logger LOG = LoggerFactory.getLogger(PolyInsights.class);

    @Autowired
    private TenantDAO tenantDAO;

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Log insight for storage
     * @param insightRecord
     */
    public Insight logInsight(InsightRequest insightRecord, String clientId, Map<String,Object> customData) {

        Optional<InsightType> optionalInsightType = validateTenantInsight(insightRecord.getTenant(), insightRecord.getType());
        Tenant tenant = tenantDAO.findOne(insightRecord.getTenant());
        InsightType insightType = optionalInsightType.get();

        if (!insightType.getValues().contains(insightRecord.getValue())) {
            LOG.warn("Insight value not accepted {}", insightRecord);
            throw new InsightNotAccepted("Insight value not accepted");
        }

        //TODO: add remote service call for checking if key exists

        String collection =  tenant.getTenant() + "." + insightType.getName();

        // check 'global' rate for posting
        Date minDate = new Date(System.currentTimeMillis() - insightType.getInterval());
        Query query = new Query(Criteria.where("clientId").is(clientId).and("date").gte(minDate));

        long count = mongoTemplate.count(query, Insight.class, collection);
        if (count != 0) {
            LOG.warn("Logging insight in interval time {}", insightRecord);
            throw new InsightNotAccepted("Logging insight too often, global limit");
        }

        // check specific key posting rate
        minDate = new Date(System.currentTimeMillis() - insightType.getSameInsightInterval());
        query = new Query(Criteria.where("key").is(insightRecord.getKey()).and("clientId").is(clientId).and("date").gte(minDate));
        count = mongoTemplate.count(query, Insight.class, collection);
        if (count != 0) {
            LOG.warn("Logging insight in interval time {}", insightRecord);
            throw new InsightNotAccepted("Logging insight too often, item limit");
        }

        Insight insight = new Insight();
        insight.setDate(new Date());
        insight.setClientId(clientId);
        insight.setKey(insightRecord.getKey());
        insight.setValue(Long.parseLong(insightRecord.getValue()));
        insight.setCustomData(customData);
        mongoTemplate.save(insight, collection);
        return insight;
    }

    /**
     * Validate tenant and insight name
     * @param tenantName
     * @param insightName
     * @return
     */
    protected Optional<InsightType> validateTenantInsight(String tenantName, String insightName) {
        Tenant tenant = tenantDAO.findOne(tenantName);
        if (tenant == null) {
            LOG.warn("No matched tenant for insight {}", tenantName);
            throw new InsightNotAccepted("Tenant not found");
        }

        Optional<InsightType> optionalInsightType = tenant.fetchInsight(insightName);
        if (!optionalInsightType.isPresent()) {
            LOG.warn("Insight type not accepted {} {}", tenant, insightName);
            throw new InsightNotAccepted("Insight type not accepted");
        }
        return optionalInsightType;
    }

    /**
     * List top insights by sum of insights, useful for likes
     * @param insightQuery
     * @return List of [{count=4, _id=test_insight}, {count=2, _id=test_insight2}]
     */
    public InsightQueryResponse listTopKeysByValueSum(InsightQuery insightQuery) {
        validateTenantInsight(insightQuery.getTenant(), insightQuery.getInsight());
        String collection =  insightQuery.getTenant() + "." + insightQuery.getInsight();

        Date startDate = insightQuery.getInterval().fetchDateFrom(new Date());

        Aggregation aggregation = newAggregation(
                match(Criteria.where("date").gte(startDate)),
                group("key").sum("value").as("count"),
                sort(Sort.Direction.DESC, "count")
        );

        AggregationResults<BasicPoly> aggregate = mongoTemplate.aggregate(aggregation, collection, BasicPoly.class);
        List<BasicPoly> response = aggregate.getMappedResults();
        LOG.info("listTopKeysByValueSum {}", response);

        InsightQueryResponse insightResponse = new InsightQueryResponse();
        insightResponse.addAll(response);
        return insightResponse;
    }

    /**
     * Fetch inisghts ordered by average value
     * @param insightQuery
     * @return [{avg=2.0, _id=test_insight}, {avg=1.5, _id=test_insight2}]
     */
    public InsightQueryResponse listTopKeysByAverageValue(InsightQuery insightQuery) {
        validateTenantInsight(insightQuery.getTenant(), insightQuery.getInsight());
        String collection =  insightQuery.getTenant() + "." + insightQuery.getInsight();

        Date startDate = insightQuery.getInterval().fetchDateFrom(new Date());

        Aggregation aggregation = newAggregation(
                match(Criteria.where("date").gte(startDate)),
                group("key").avg("value").as("avg"),
                sort(Sort.Direction.DESC, "avg")
        );

        AggregationResults<BasicPoly> aggregate = mongoTemplate.aggregate(aggregation, collection, BasicPoly.class);
        List<BasicPoly> response = aggregate.getMappedResults();
        LOG.info("listTopKeysByAverageValue {}", response);

        InsightQueryResponse insightResponse = new InsightQueryResponse();
        insightResponse.addAll(response);
        return insightResponse;
    }

    /**
     * Fetch inisght values stats
     * @param insightQuery
     * @return
     */
    public InsightQueryResponse fetchInsightStatsByKey(InsightQuery insightQuery) {
        validateTenantInsight(insightQuery.getTenant(), insightQuery.getInsight());
        String collection =  insightQuery.getTenant() + "." + insightQuery.getInsight();

        Date startDate = insightQuery.getInterval().fetchDateFrom(new Date());

        Aggregation aggregation = newAggregation(
                match(Criteria.where("key").is(insightQuery.getKey()).and("date").gte(startDate)),
                group("key").push("value").as("keys")
        );

        AggregationResults<BasicPoly> aggregate = mongoTemplate.aggregate(aggregation, collection, BasicPoly.class);
        BasicPoly mappedResult = aggregate.getUniqueMappedResult();
        LOG.info("fetchInsightStatsByKey {}", mappedResult); //{keys=[2, 1], _id=test_insight2}

        BasicPoly stats = new BasicPoly();
        stats._id(mappedResult._id());
        List<Long> keys = (List<Long>) mappedResult.get("keys");
        Map<Long, Long> map = new HashMap<>();
        keys.forEach( key -> {
            if (!map.containsKey(key)) {
                map.put(key, 0L);
            }
            map.put(key, map.get(key) + 1);
        });
        stats.put("stats", map);
        InsightQueryResponse insightResponse = new InsightQueryResponse();
        insightResponse.add(stats);

        LOG.info("fetchInsightStatsByKey response {}", insightResponse);
        return insightResponse;
    }

}