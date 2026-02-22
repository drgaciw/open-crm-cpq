package com.opencrm.pricingairules.service.fallback;

import com.opencrm.pricingairules.ai.sequential.RuleAnalysisResult;
import com.opencrm.pricingairules.ai.sequential.TestCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Fallback service that provides default responses when AI services are
 * unavailable
 */
@Service
@Slf4j
public class AIFallbackService {

    /**
     * Provide a fallback rule analysis when the AI service is unavailable
     * 
     * @param businessRequirement The business requirement
     * @return A basic rule analysis
     */
    public RuleAnalysisResult fallbackRuleAnalysis(String businessRequirement) {
        log.warn("Using fallback rule analysis for business requirement: {}",
                truncate(businessRequirement, 100));

        RuleAnalysisResult result = new RuleAnalysisResult();

        // Extract keywords from the business requirement
        List<String> keywords = extractKeywords(businessRequirement);

        // Set basic conditions based on keywords
        List<String> conditions = new ArrayList<>();
        for (String keyword : keywords) {
            if (isConditionKeyword(keyword)) {
                conditions.add(String.format("%s == value", keyword.toLowerCase()));
            }
        }
        if (conditions.isEmpty()) {
            conditions.add("true");
        }
        result.setConditions(conditions);

        // Set basic actions
        List<String> actions = new ArrayList<>();
        if (businessRequirement.contains("discount")) {
            actions.add("Apply discount");
        } else if (businessRequirement.contains("tax")) {
            actions.add("Calculate tax");
        } else if (businessRequirement.contains("price")) {
            actions.add("Set price");
        } else {
            actions.add("Perform action");
        }
        result.setActions(actions);

        // Set basic required inputs
        result.setRequiredInputs(Arrays.asList("Customer", "Product", "Quote"));

        // Set basic expected outputs
        result.setExpectedOutputs(Arrays.asList("Updated Quote"));

        return result;
    }

    /**
     * Provide a fallback rule content when the AI service is unavailable
     * 
     * @param businessRequirement The business requirement
     * @return A basic rule implementation
     */
    public String fallbackRuleContent(String businessRequirement) {
        log.warn("Using fallback rule content for business requirement: {}",
                truncate(businessRequirement, 100));

        return """
                rule "Fallback Rule"
                    when
                        $quote : Quote()
                        $customer : Customer(tier != null)
                        $product : Product(name != null)
                    then
                        System.out.println("Fallback rule executed for " + $customer.getName() +
                            " and product " + $product.getName());
                        // Fallback logic generated based on requirements
                        double discount = 0.0;
                        if ("%s".toLowerCase().contains("discount")) {
                            discount = 0.10; // Default 10%% discount
                        }

                        if ("%s".toLowerCase().contains("vip") || "%s".toLowerCase().contains("enterprise")) {
                            discount += 0.05; // Extra 5%% for VIP/Enterprise
                        }

                        if (discount > 0) {
                            System.out.println("Applying fallback discount: " + discount);
                            // In a real rule, we would modify the quote or price here
                            // $quote.setDiscount(discount);
                        }
                """.formatted(sanitize(businessRequirement));
    }

    /**
     * Provide fallback test cases when the AI service is unavailable
     * 
     * @param ruleContent The rule content
     * @return A list of basic test cases
     */
    public List<TestCase> fallbackTestCases(String ruleContent) {
        log.warn("Using fallback test cases for rule");

        TestCase testCase1 = new TestCase();
        testCase1.setId("TC001");
        testCase1.setDescription("Basic test case");
        Map<String, Object> input1 = new HashMap<>();
        input1.put("customerTier", "ENTERPRISE");
        input1.put("productName", "Standard License");
        testCase1.setInput(input1);

        Map<String, Object> output1 = new HashMap<>();
        output1.put("result", true);
        testCase1.setExpectedOutput(output1);

        TestCase testCase2 = new TestCase();
        testCase2.setId("TC002");
        testCase2.setDescription("Alternative test case");
        Map<String, Object> input2 = new HashMap<>();
        input2.put("customerTier", "STANDARD");
        input2.put("productName", "Premium License");
        testCase2.setInput(input2);

        Map<String, Object> output2 = new HashMap<>();
        output2.put("result", false);
        testCase2.setExpectedOutput(output2);

        return Arrays.asList(testCase1, testCase2);
    }

    /**
     * Provide a fallback rule optimization when the AI service is unavailable
     * 
     * @param ruleContent The rule content to optimize
     * @return The same rule content (no optimization)
     */
    public String fallbackRuleOptimization(String ruleContent) {
        log.warn("Using fallback rule optimization (no changes)");
        return ruleContent;
    }

    // Helper methods

    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        String[] words = text.split("\\s+");

        for (String word : words) {
            word = word.replaceAll("[^a-zA-Z]", "");
            if (word.length() > 3 && Character.isUpperCase(word.charAt(0))) {
                keywords.add(word);
            }
        }

        // Add default keywords if none found
        if (keywords.isEmpty()) {
            keywords.add("Customer");
            keywords.add("Product");
        }

        return keywords;
    }

    private boolean isConditionKeyword(String keyword) {
        List<String> conditionWords = Arrays.asList(
                "Customer", "Product", "Order", "Quote", "Price", "Discount", "Tax", "Shipping");
        return conditionWords.contains(keyword);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\"'\\\\]", "");
    }
}
