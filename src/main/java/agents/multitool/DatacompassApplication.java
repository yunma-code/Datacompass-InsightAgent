package agents.multitool;

import com.google.adk.agents.BaseAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import agents.multitool.llmAgents.DatacompassAgent;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class DatacompassApplication {
    public static void main(String[] args) {
        System.out.println("Starting Datacompass Agent...");

        try {
            // Get ROOT_AGENT from DatacompassAgent
            BaseAgent rootAgent = DatacompassAgent.ROOT_AGENT;
            System.out.println("Agent loaded successfully: " + rootAgent.name());

            // InMemoryRunner
            InMemoryRunner runner = new InMemoryRunner(rootAgent, "DatacompassAgent");

            // Create session
            Session session = runner.sessionService()
                    .createSession("DatacompassAgent", "user_123")
                    .blockingGet();

            System.out.println("Agent is ready! Type 'quit' to exit.");
            System.out.println("Example input: {\"companyName\": \"TechCorp\", \"industry\": \"SaaS\", \"stage\": \"Series A\", \"revenueRange\": \"$1M-$5M\"}");

            // CLI
            try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
                while (true) {
                    System.out.print("\nYou > ");
                    String userInput = scanner.nextLine();

                    if ("quit".equalsIgnoreCase(userInput)) {
                        break;
                    }

                    if (userInput.trim().isEmpty()) {
                        continue;
                    }

                    Content userMsg = Content.fromParts(Part.fromText(userInput)); // user msg

                    // Run the agent
                    Flowable<Event> events = runner.runAsync("user_123", session.id(), userMsg);

                    System.out.print("\nAgent > ");
                    events.blockingForEach(event -> {
                        if (event.finalResponse()) {
                            System.out.println(event.stringifyContent());
                        }
                    });
                }
            }

        } catch (Exception e) {
            System.err.println("Error starting agent: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Goodbye!");
    }
}

//package agents.multitool;
//
//import com.google.adk.agents.BaseAgent;
//import com.google.adk.development.DevServer;
//import agents.multitool.llmAgents.DatacompassAgent;
//
//public class DatacompassApplication {
//    public static void main(String[] args) {
//        System.out.println("Starting Datacompass Agent with ADK Dev UI...");
//
//        try {
//            // Load the root agent
//            BaseAgent rootAgent = DatacompassAgent.ROOT_AGENT;
//            System.out.println("Agent loaded: " + rootAgent.name());
//
//            // Start the ADK Dev UI server on localhost:8080
//            DevServer devServer = new DevServer(rootAgent);
//            devServer.start();
//
//            System.out.println("Dev UI running at http://localhost:8080");
//
//            // Keep the server alive
//            Thread.currentThread().join();
//        } catch (Exception e) {
//            System.err.println("Error starting Dev UI: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//}
