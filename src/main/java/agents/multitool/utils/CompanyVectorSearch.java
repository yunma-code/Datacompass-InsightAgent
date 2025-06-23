package agents.multitool.utils;

import com.google.cloud.bigquery.*;

import com.google.adk.tools.Annotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompanyVectorSearch {

        
    private static final String PROJECT_ID = "hopeful-list-463100-s6";
    private static final String DATASET_ID = "crunchbasedataset";
    private static final String EMBEDDING_TABLE = "companies_embeddings_ml";
    private static final String COMPANIES_TABLE = "companies";
    private static final String MODEL_NAME = "text_embedding_model";

    public static Map<String, Object> getSimilarCompany(@Annotations.Schema(name = "name", description = "name of the company") String name,
                                           @Annotations.Schema(name = "industry", description = "the industry which the start up company belongs to") String industry,
                                           @Annotations.Schema(name = "stage", description = "The funding stage of the company") String stage,
                                           @Annotations.Schema(name = "revenue", description = "The annual revenue of the company. Should be provided as a range") String revenue) {
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> companies = new ArrayList<>();
        
        try {
            BigQuery bigquery = BigQueryOptions.newBuilder()
                    .setProjectId(PROJECT_ID)
                    .build()
                    .getService();

            // Create input content for embedding
            String inputContent = String.format(
                "Company Name: %s. Industry: %s. Funding Stage: %s. Revenue Range: %s. " +
                "This is a startup company in the %s industry at %s stage with %s revenue.",
                name, industry, stage, revenue, industry, stage, revenue
            );

            // Build the query using VECTOR_SEARCH
            String sql = String.format(
                "WITH InputEmbedding AS (\n" +
                "  SELECT ml_generate_embedding_result AS input_embedding \n" +
                "  FROM ML.GENERATE_EMBEDDING(\n" +
                "    MODEL `%s.%s.%s`, \n" +
                "    (SELECT '%s' AS content), \n" +
                "    STRUCT(\n" +
                "      TRUE AS flatten_json_output, \n" +
                "      'RETRIEVAL_DOCUMENT' AS task_type, \n" +
                "      256 AS output_dimensionality\n" +
                "    )\n" +
                "  )\n" +
                ")\n" +
                "SELECT \n" +
                "  base.base.company_id,\n" +
                "  base.base.content,\n" +
                "  base.distance,\n" +
                "  c.name,\n" +
                "  c.category_list,\n" +
                "  c.` market `,\n" +
                "  c.` funding_total_usd `,\n" +
                "  c.status,\n" +
                "  c.funding_rounds,\n" +
                "  c.founded_year,\n" +
                "  c.round_A,\n" +
                "  c.round_B,\n" +
                "  c.round_C,\n" +
                "  c.round_D\n" +
                "FROM VECTOR_SEARCH(\n" +
                "  TABLE `%s.%s.%s`, \n" +
                "  'ml_generate_embedding_result', \n" +
                "  (SELECT input_embedding FROM InputEmbedding), \n" +
                "  distance_type => 'COSINE', \n" +
                "  top_k => 5\n" +
                ") base\n" +
                "JOIN `%s.%s.%s` c ON base.base.company_id = c.company_id\n" +
                "WHERE base.base.company_id IS NOT NULL\n" +
                "ORDER BY base.distance ASC\n" +
                "LIMIT 5",
                PROJECT_ID, DATASET_ID, MODEL_NAME, inputContent,
                PROJECT_ID, DATASET_ID, EMBEDDING_TABLE,
                PROJECT_ID, DATASET_ID, COMPANIES_TABLE
            );

            System.out.println("Executing vector search query...");
            
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(sql)
                    .setUseLegacySql(false)
                    .build();

            Job queryJob = bigquery.create(JobInfo.newBuilder(queryConfig).build());
            queryJob = queryJob.waitFor();

            if (queryJob == null) {
                throw new RuntimeException("Vector search job no longer exists.");
            }

            if (queryJob.getStatus().getError() != null) {
                throw new RuntimeException("Vector search failed: " + queryJob.getStatus().getError().toString());
            }

            // Process results
            TableResult tableResult = queryJob.getQueryResults();
            
            boolean hasResults = false;
            for (FieldValueList row : tableResult.iterateAll()) {
                hasResults = true;
                Map<String, Object> company = new HashMap<>();
                company.put("company_id", row.get("company_id").getValue());
                company.put("content", row.get("content").getValue());
                company.put("similarity_score", 1.0 - row.get("distance").getDoubleValue());
                company.put("name", row.get("name").getValue());
                company.put("category_list", row.get("category_list").getValue());
                company.put("market", row.get(" market ").getValue());
                company.put("funding_total_usd", row.get(" funding_total_usd ").getValue());
                company.put("status", row.get("status").getValue());
                company.put("funding_rounds", row.get("funding_rounds").getValue());
                company.put("founded_year", row.get("founded_year").getValue());
                company.put("round_A", row.get("round_A").getValue());
                company.put("round_B", row.get("round_B").getValue());
                company.put("round_C", row.get("round_C").getValue());
                company.put("round_D", row.get("round_D").getValue());
                
                companies.add(company);
            }
            
            if (hasResults) {
                result.put("status", "success");
                result.put("message", "Found " + companies.size() + " similar companies");
                result.put("companies", companies);
            } else {
                result.put("status", "no_results");
                result.put("message", "No similar companies found");
                result.put("companies", companies);
            }

        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Error during vector search: " + e.getMessage());
            result.put("companies", companies);
            e.printStackTrace();
        }

        return result;
    }

    public static Map<String, Object> debugEmbeddings() {
        try {
            BigQuery bigQuery = BigQueryOptions.getDefaultInstance().getService();
            
            // Test 1: Check if embeddings table has data
            String checkEmbeddingsQuery = 
                    "SELECT COUNT(*) as total_embeddings, " +
                    "       COUNT(CASE WHEN ml_generate_embedding_status = 'SUCCESS' THEN 1 END) as successful_embeddings " +
                    "FROM `hopeful-list-463100-s6.crunchbasedataset.companies_embeddings_ml`";
            
            TableResult embeddingsResult = bigQuery.query(QueryJobConfiguration.newBuilder(checkEmbeddingsQuery).build());
            
            // Test 2: Check if companies table has data
            String checkCompaniesQuery = 
                    "SELECT COUNT(*) as total_companies " +
                    "FROM `hopeful-list-463100-s6.crunchbasedataset.companies`";
            
            TableResult companiesResult = bigQuery.query(QueryJobConfiguration.newBuilder(checkCompaniesQuery).build());
            
            Map<String, Object> debugResult = new HashMap<>();
            
            for (FieldValueList row : embeddingsResult.iterateAll()) {
                debugResult.put("total_embeddings", row.get("total_embeddings").getLongValue());
                debugResult.put("successful_embeddings", row.get("successful_embeddings").getLongValue());
            }
            
            for (FieldValueList row : companiesResult.iterateAll()) {
                debugResult.put("total_companies", row.get("total_companies").getLongValue());
            }
            
            return debugResult;
            
        } catch (Exception e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
}
