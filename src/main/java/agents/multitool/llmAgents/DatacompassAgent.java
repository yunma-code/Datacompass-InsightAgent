package agents.multitool.llmAgents;

import com.google.adk.agents.BaseAgent;
import com.google.genai.types.Schema;

import java.util.Map;

public class DatacompassAgent  {
    private static String NAME = "datacompass_workflow_agent";
    private static String USER_ID = "user_123";
    private static String APP_NAME = "DatacompassWorkflowAgent";
    private static String MODEL_NAME = "gemini-2.5-flash";
    private static final String SESSION_ID_TOOL_AGENT = "session_tool_agent_xyz";
    private static final String SESSION_ID_SCHEMA_AGENT = "session_schema_agent_xyz";

    private static final Schema INPUT_SCHEMA =
            Schema.builder()
                    .type("OBJECT")
                    .description("Input Schema: should be JSON with Company industry, stage, revenue range")
                    .properties(Map.of(
                            "companyName", Schema.builder().type("STRING").build(),
                            "industry", Schema.builder().type("STRING").build(),
                            "stage", Schema.builder().type("STRING").build(),
                            "revenueRange", Schema.builder().type("STRING").build()
                    ))
            .build();

    public static BaseAgent ROOT_AGENT = initAgent();

    private static BaseAgent initAgent(){
        // workflow agent here
        return AnalysisWorkflowAgent.createWorkflowAgent();
        
    }
}
