package agents.multitool.llmAgents;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.tools.FunctionTool;
import agents.multitool.utils.CompanyVectorSearch;

public class AnalysisWorkflowAgent {
    private static final String MODEL_NAME = "gemini-2.5-flash";

    public static BaseAgent createWorkflowAgent() {
        FunctionTool vectorSearchTool = FunctionTool.create(CompanyVectorSearch.class, "getSimilarCompany");

        // First agent: Company Analysis Agent
        LlmAgent analysisAgent = LlmAgent.builder()
                .model(MODEL_NAME)
                .name("company_analysis_agent")
                .description("Analyzes user input and determines industry & comparable companies.")
                .instruction(
                        "You are a startup analysis assistant. Your role is to analyze structured input about a startup company and generate actionable insights and observations.\n" +
                        "Your tasks:\n" +
                        "  1. Summarize the company profile in 1–2 sentences.\n" +
                        "  2. Use the vector search tool to find the top 5 most similar companies for benchmarking.\n" +
                        "  3. Display the top 5 competitors with their key details (name, market, funding, similarity score).\n" +
                        "  4. Evaluate the typical characteristics and challenges of companies at this stage in this industry.\n" +
                        "  5. Suggest 2–3 growth opportunities or strategic priorities based on the company's stage and revenue range.\n" +
                        "  6. Mention any common risks or red flags associated with similar startups.\n" +
                        "  7. Keep your tone professional, insightful, and concise.\n" +
                        "  8. Format the top 5 competitors in a clear table or list format."
                )
                .tools(vectorSearchTool)
                .outputKey("company_analysis")
                .build();

        // Second agent: Benchmark Report Agent
        LlmAgent benchmarkAgent = LlmAgent.builder()
                .model(MODEL_NAME)
                .name("benchmark_report_agent")
                .description("Queries startup databases and builds a comprehensive benchmarking report.")
                .instruction(
                        "You are a benchmark analysis specialist. Based on the company analysis provided, create a comprehensive benchmarking report.\n" +
                        "Your tasks:\n" +
                        "  1. Review the company analysis from the previous agent.\n" +
                        "  2. Use the vector search tool to find the top 5 most similar companies.\n" +
                        "  3. Display the top 5 competitors with detailed information including:\n" +
                        "     - Company name and similarity score\n" +
                        "     - Market/category and funding stage\n" +
                        "     - Total funding amount and funding rounds\n" +
                        "     - Founded year and current status\n" +
                        "  4. Create a detailed benchmarking report including:\n" +
                        "     - Market positioning analysis based on the top 5 competitors\n" +
                        "     - Competitive landscape overview\n" +
                        "     - Funding patterns and trends from similar companies\n" +
                        "     - Regional market insights\n" +
                        "     - Strategic recommendations based on comparable companies\n" +
                        "  5. Provide actionable insights for strategic decision-making.\n" +
                        "  6. Format the report in a clear, structured manner with the top 5 competitors prominently displayed."
                )
                .tools(vectorSearchTool)
                .outputKey("benchmark_report")
                .build();

        // Create the sequential workflow
        return SequentialAgent.builder()
                .name("CompanyAnalysisWorkflow")
                .description("Executes a sequence of company analyzing and benchmark report generating.")
                .subAgents(analysisAgent, benchmarkAgent)
                .build();
    }
} 