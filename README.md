# Datacompass Agent: AI-Powered Startup Analysis

* Built for the [Agent Development Kit Hackathon](https://google.github.io/adk-docs/) with Google Cloud**  
* Category: *Data Analysis and Insights* – Create multi-agent systems that autonomously analyze data from various sources, derive meaningful insights using tools like BigQuery, and collaboratively present findings**

---

Datacompass is an intelligent multi-agent system that provides data-driven insights into startup companies. Designed for analysts, investors, and founders, the agent uses natural language processing, vector-based similarity search, and benchmarking tools to evaluate a company's position in the startup ecosystem.

It is built using **Google's Agent Development Kit (ADK)** and integrated with **BigQuery**, **BigQuery ML**, and **Vertex AI**, demonstrating how autonomous agents can leverage cloud-based data platforms to derive and present meaningful business insights.

##  Key Features

- **Natural Language Understanding**: Describe a startup in plain English and let the agent extract relevant attributes.
- **Vector-Based Similarity Search**: Uses BigQuery ML and Vertex AI-generated embeddings to find startups with similar characteristics.
- **End-to-End Insight Generation**: Combines profiling, analysis, and benchmarking into a single agent workflow.
- **Startup Benchmarking**: Compares a startup’s funding, industry, and growth stage with its peers.
- **UI for Exploration**: Bundled with ADK Dev UI for real-time, browser-based interactions and testing.

##  Dataset Information

We use a curated dataset of startups derived from:

> [Startup Investments - Crunchbase API (Kaggle)](https://www.kaggle.com/datasets/arindam235/startup-investments-crunchbase)

This dataset contains information on companies, industries, funding rounds, investment firms, and acquisition details. It was cleaned and uploaded to BigQuery with two primary tables:

- **`companies`**: Contains structured company metadata (e.g., name, industry, revenue, funding round)
- **`companies_embeddings_ml`**: Stores vector embeddings for each company generated using BigQuery ML + Vertex AI

##  Vector-Based Similarity Search in BigQuery

To find startups similar to a given company profile, we:

1. **Generate an Input Embedding**:
    - From user input (industry, funding stage, revenue), a Java tool invokes BigQuery ML to call Vertex AI's embedding model.
    - This is done via a SQL function like:

   ```sql
   SELECT *
   FROM ML.GENERATE_EMBEDDING(
     MODEL `project_id.dataset.embedding_model`,
     (SELECT 'CloudScale, Series B, $6M revenue, cloud computing industry' AS text_input),
     STRUCT(TRUE AS flatten_json)
   );
   ```

2. **Perform a Vector Search**:
    - Using BigQuery’s `VECTOR_SEARCH` function, we search for companies with similar embeddings in the `companies_embeddings_ml` table.

   ```sql
   SELECT c.*, v.score
   FROM VECTOR_SEARCH(
     TABLE `dataset.companies_embeddings_ml`,
     'embedding',
     (SELECT embedding FROM input_embedding),
     top_k => 5
   ) AS v
   JOIN `dataset.companies` AS c
   ON c.company_id = v.id
   ORDER BY v.score DESC;
   ```

3. **Join and Format Results**:
    - Results are joined with the `companies` metadata table to extract industry, location, and funding information for comparison and visualization.

This entire flow is encapsulated in a **Java class** (`GetSimilarCompanyTool.java`) that handles the input, executes the query, and returns structured results for benchmarking.

##  Agent Architecture

This system uses a **multi-agent sequential workflow**:

1. **Analysis Agent**  
   Understands the startup description and generates a structured profile (industry, stage, revenue).

2. **Benchmarking Agent**
    - Uses the profile to perform a vector search.
    - Retrieves similar companies from BigQuery.
    - Synthesizes the data into an insight report for the user.

##  How to Run the Agent

### Prerequisites

- Java 17+
- Apache Maven
- Google Cloud Project with:
    - BigQuery & Vertex AI enabled
    - Tables: `companies`, `companies_embeddings_ml`
    - Proper IAM permissions and GCP authentication
- Dataset uploaded to BigQuery from the [Kaggle Crunchbase dataset](https://www.kaggle.com/datasets/arindam235/startup-investments-crunchbase)

### Run via IntelliJ

1. Open Maven Tool Window → Reload All Maven Projects
2. Navigate to `exec:java` under the `exec` plugin
3. Run the goal → ADK Dev Server starts
4. Go to [http://localhost:8080](http://localhost:8080)

### Run via Terminal

```bash
mvn compile
mvn exec:java
```

Then visit: **http://localhost:8080**

## Deployed project URL: 
Go to [https://datacompass-463086862501.us-central1.run.app/dev-ui?app=CompanyAnalysisWorkflow]

##  Example Queries

> **"Analyze a company called CloudScale. We’re Series B, cloud computing, and earn $6M in revenue."**

> **"Find companies similar to a SaaS startup with $3M revenue in Series A."**

> **"Company: DataSync, Industry: Fintech, Stage: Seed, Revenue: $500k."**


# Findings and Learnings


3. how does sequential workflow agent allow us to process data flow and generate content that interact with database(bigQuery)
### 1. Multi-Agent Architecture with `google-adk`

We implemented a multi-agent system using the `SequentialAgent` from the Google ADK. This architecture involves chaining multiple specialized agents together, where the output of one agent becomes the input for the next.

-   **Modularity:** Our two agents—`analysisAgent` and `benchmarkAgent`—each have a distinct responsibility. The first handles initial interpretation and analysis and vector search for competitors, while the second focuses on data retrieval and benchmarking. This separation of concerns makes the system easier to develop, debug, and extend.
-   **Orchestration:** The `SequentialAgent` acts as an orchestrator agent, managing the execution flow automatically. This simplifies the top-level logic, as we only need to define the sequence of agents, and the ADK handles the state management and data passing between them.

### 2. Vector Embedding and Search with BigQuery ML

A core feature of this system is its ability to find "similar" companies, which is powered by vector search in BigQuery.

-   **Embedding Generation:** We use the `ML.GENERATE_EMBEDDING` function with a Vertex AI model (`text-embedding-004`) to convert textual company descriptions into numerical vectors (embeddings). This is done for both the user's input company at query time and for all companies in the source database ahead of time.
-   **Vector Search:** The `VECTOR_SEARCH` function is used to compare the user's input embedding against the pre-calculated embeddings in the `companies_embeddings_ml` table. It uses `COSINE` distance to find the vectors that are semantically closest, effectively identifying the most similar companies.
-   **Efficiency:** By performing the search directly within BigQuery, we avoid the need to pull large amounts of data into the application, making the process scalable and efficient.

### 3. Data Flow in a Sequential Workflow Agent

The `SequentialAgent` provides a clear pattern for processing data and interacting with external systems like BigQuery.

-   **Initial Input:** The workflow begins when the user provides a natural language query.
-   **Agent-to-Agent Data Flow:** The `analysisAgent` processes this input and its output (a structured analysis of the company) is automatically passed as the input to the `benchmarkAgent`.
-   **Interaction with BigQuery via Function Tools:** The `benchmarkAgent` is equipped with a `FunctionTool` (`getSimilarCompany`). The agent's underlying language model intelligently decides when to call this tool and extracts the necessary parameters (name, industry, etc.) from the text it received.
-   **Database Query:** The `getSimilarCompany` Java method executes the `VECTOR_SEARCH` SQL query against BigQuery.
-   **Returning Data to the Agent:** The query results (a list of similar companies) are returned from the Java method back to the `benchmarkAgent`.
-   **Final Content Generation:** The `benchmarkAgent` receives this structured data from the tool and uses it to generate the final, user-facing benchmarking report, completing the workflow.

This entire process demonstrates how agents can seamlessly transition between generative AI tasks and data-driven tool execution to solve complex problems.

