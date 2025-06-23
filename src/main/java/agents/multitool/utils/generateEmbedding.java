package agents.multitool.utils;

import com.google.cloud.bigquery.*;

public class generateEmbedding {

  private static final String PROJECT_ID = "hopeful-list-463100-s6";
  private static final String DATASET_ID = "crunchbasedataset";
  private static final String SOURCE_TABLE = "companies";
  private static final String EMBEDDING_TABLE = "companies_embeddings_ml";
  private static final String CONNECTION_ID = "vertex-ai-connection";
  private static final String MODEL_NAME = "text_embedding_model";
  private static final String REGION = "us"; // Change to your region

  public static void main(String[] args) {
    BigQuery bigquery = BigQueryOptions.newBuilder()
            .setProjectId(PROJECT_ID)
            .build()
            .getService();

    try {
      // Step 1: Create the connection (if not exists)
      System.out.println("Note: Make sure you have created the BigQuery connection to Vertex AI");
      System.out.println("Connection ID should be: " + PROJECT_ID + "." + REGION + "." + CONNECTION_ID);

      // Step 2: Create the remote model
      createRemoteModel(bigquery);

      // Step 3: Generate embeddings using the model
      generateEmbeddings(bigquery);

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static void createRemoteModel(BigQuery bigquery) throws InterruptedException {
    // Create remote model that references Vertex AI text-embedding-004
    String createModelSql = String.format(
            "CREATE OR REPLACE MODEL `%s.%s.%s`\n" +
                    "REMOTE WITH CONNECTION `%s.%s.%s`\n" +
                    "OPTIONS (\n" +
                    "  ENDPOINT = 'text-embedding-004'\n" +
                    ")",
            PROJECT_ID, DATASET_ID, MODEL_NAME,
            PROJECT_ID, REGION, CONNECTION_ID
    );

    System.out.println("Creating remote model for text embeddings...");
    System.out.println("SQL: " + createModelSql);

    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(createModelSql)
            .setUseLegacySql(false)
            .build();

    Job queryJob = bigquery.create(JobInfo.newBuilder(queryConfig).build());
    queryJob = queryJob.waitFor();

    if (queryJob == null) {
      throw new RuntimeException("Model creation job no longer exists.");
    }

    if (queryJob.getStatus().getError() != null) {
      throw new RuntimeException("Model creation failed: " + queryJob.getStatus().getError().toString());
    }

    System.out.println("Remote model created successfully!");
  }

  private static void generateEmbeddings(BigQuery bigquery) throws InterruptedException {
    // Check if data in the source table
    String countSql = String.format(
            "SELECT COUNT(*) as total_companies\n" +
                    "FROM `%s.%s.%s`\n" +
                    "WHERE name IS NOT NULL AND TRIM(name) != ''",
            PROJECT_ID, DATASET_ID, SOURCE_TABLE
    );

    System.out.println("Checking source data...");
    TableResult countResult = executeQuery(bigquery, countSql);
    for (FieldValueList row : countResult.iterateAll()) {
      System.out.printf("Found %d companies with valid names%n", row.get("total_companies").getLongValue());
    }


    String embeddingSql = String.format(
            "CREATE OR REPLACE TABLE `%s.%s.%s` AS\n" +
                    "SELECT * FROM ML.GENERATE_EMBEDDING(\n" +
                    "  MODEL `%s.%s.%s`,\n" +
                    "  (\n" +
                    "    SELECT\n" +
                    "      company_id,\n" +
                    "      name AS content  -- ML.GENERATE_EMBEDDING expects a 'content' column\n" +
                    "    FROM `%s.%s.%s`\n" +
                    "    WHERE name IS NOT NULL AND TRIM(name) != ''\n" +
                    "  ),\n" +
                    "  STRUCT(\n" +
                    "    TRUE AS flatten_json_output,\n" +
                    "    'RETRIEVAL_DOCUMENT' AS task_type,\n" +
                    "    256 AS output_dimensionality  -- Optional: specify embedding dimensions\n" +
                    "  )\n" +
                    ")",
            PROJECT_ID, DATASET_ID, EMBEDDING_TABLE,
            PROJECT_ID, DATASET_ID, MODEL_NAME,
            PROJECT_ID, DATASET_ID, SOURCE_TABLE
    );

    System.out.println("Starting embedding generation...");
    System.out.println("SQL: " + embeddingSql);

    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(embeddingSql)
            .setUseLegacySql(false)
            .build();

    Job queryJob = bigquery.create(JobInfo.newBuilder(queryConfig).build());
    queryJob = queryJob.waitFor();

    if (queryJob == null) {
      throw new RuntimeException("Embedding generation job no longer exists.");
    }

    if (queryJob.getStatus().getError() != null) {
      throw new RuntimeException("Embedding generation failed: " + queryJob.getStatus().getError().toString());
    }

    System.out.printf("Embedding generation completed! Table created: %s.%s.%s%n",
            PROJECT_ID, DATASET_ID, EMBEDDING_TABLE);

    verifyResults(bigquery);
  }

  private static void verifyResults(BigQuery bigquery) throws InterruptedException {
    String verifySql = String.format(
            "SELECT\n" +
                    "  company_id,\n" +
                    "  content,\n" +
                    "  ARRAY_LENGTH(ml_generate_embedding_result) as embedding_dimension\n" +
                    "FROM `%s.%s.%s`\n" +
                    "LIMIT 5",
            PROJECT_ID, DATASET_ID, EMBEDDING_TABLE
    );

    System.out.println("Verifying results...");
    TableResult result = executeQuery(bigquery, verifySql);

    System.out.println("Sample results:");
    System.out.println("Company ID | Company Name | Embedding Dimension");
    System.out.println("-----------|--------------|-------------------");

    for (FieldValueList row : result.iterateAll()) {
      System.out.printf("%s | %s | %d%n",
              row.get("company_id").getStringValue(),
              row.get("content").getStringValue(),
              row.get("embedding_dimension").getLongValue());
    }

    // Get total count
    String totalCountSql = String.format(
            "SELECT COUNT(*) as total_embeddings FROM `%s.%s.%s`",
            PROJECT_ID, DATASET_ID, EMBEDDING_TABLE
    );

    TableResult totalResult = executeQuery(bigquery, totalCountSql);
    for (FieldValueList row : totalResult.iterateAll()) {
      System.out.printf("Total embeddings generated: %d%n", row.get("total_embeddings").getLongValue());
    }

    System.out.println("\nYou can now query the embeddings using:");
    System.out.printf("SELECT * FROM `%s.%s.%s` LIMIT 10;%n", PROJECT_ID, DATASET_ID, EMBEDDING_TABLE);

    System.out.println("\nTo find similar companies using vector search:");
    System.out.printf(
            "WITH target_embedding AS (\n" +
                    "  SELECT ml_generate_embedding_result FROM `%s.%s.%s` WHERE content = 'TARGET_COMPANY_NAME'\n" +
                    ")\n" +
                    "SELECT\n" +
                    "  base.content,\n" +
                    "  distance\n" +
                    "FROM VECTOR_SEARCH(\n" +
                    "  TABLE `%s.%s.%s`, 'ml_generate_embedding_result',\n" +
                    "  (SELECT ml_generate_embedding_result FROM target_embedding),\n" +
                    "  distance_type => 'COSINE',\n" +
                    "  top_k => 10\n" +
                    ")\n" +
                    "ORDER BY distance;%n",
            PROJECT_ID, DATASET_ID, EMBEDDING_TABLE,
            PROJECT_ID, DATASET_ID, EMBEDDING_TABLE);
  }

  private static TableResult executeQuery(BigQuery bigquery, String sql) throws InterruptedException {
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(sql)
            .setUseLegacySql(false)
            .build();

    Job queryJob = bigquery.create(JobInfo.newBuilder(queryConfig).build());
    queryJob = queryJob.waitFor();

    if (queryJob == null) {
      throw new RuntimeException("Query job no longer exists.");
    }

    if (queryJob.getStatus().getError() != null) {
      throw new RuntimeException("Query failed: " + queryJob.getStatus().getError().toString());
    }

    return queryJob.getQueryResults();
  }


//   public static void printConnectionCreationInstructions() {
//     System.out.println("=== SETUP INSTRUCTIONS ===");
//     System.out.println("Before running this code, you need to create a BigQuery connection to Vertex AI:");
//     System.out.println();
//     System.out.println("1. Create the connection using bq command:");
//     System.out.printf("   bq mk --connection --location=%s --project_id=%s --connection_type=CLOUD_RESOURCE %s%n",
//             REGION, PROJECT_ID, CONNECTION_ID);
//     System.out.println();
//     System.out.println("2. Get the service account:");
//     System.out.printf("   bq show --connection %s.%s.%s%n", PROJECT_ID, REGION, CONNECTION_ID);
//     System.out.println();
//     System.out.println("3. Grant Vertex AI User role to the service account:");
//     System.out.println("   gcloud projects add-iam-policy-binding " + PROJECT_ID +
//             " --member='serviceAccount:SERVICE_ACCOUNT_EMAIL' --role='roles/aiplatform.user'");
//     System.out.println();
//     System.out.println("4. Then run this Java application.");
//     System.out.println("=== END SETUP INSTRUCTIONS ===");
//   }
}

