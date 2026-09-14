package com.opencrm.rules.drools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One Drools compiler diagnostic, mapped from {@code org.kie.api.builder.Message}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroolsCompilationMessage {

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    private String code;
    private String message;
    private Severity severity;
    private Integer line;
    private Integer column;
    private String path;

    public String format() {
        StringBuilder sb = new StringBuilder();
        if (line != null && line >= 0) {
            sb.append("line ").append(line);
            if (column != null && column >= 0) {
                sb.append(':').append(column);
            }
            sb.append(' ');
        }
        if (message != null) {
            sb.append(message);
        }
        return sb.toString();
    }
}
