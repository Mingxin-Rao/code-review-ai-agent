package com.codeguardian.service.ai.context;

import com.codeguardian.entity.Finding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Review context holder
 * Used to pass issues discovered by tools within a thread
 */
public class ReviewContextHolder {
    private static final ThreadLocal<List<Finding>> findingsHolder = ThreadLocal.withInitial(ArrayList::new);

    public static void addFindings(List<Finding> findings) {
        if (findings != null) {
            findingsHolder.get().addAll(findings);
        }
    }
    
    public static void addFinding(Finding finding) {
        if (finding != null) {
            findingsHolder.get().add(finding);
        }
    }

    public static List<Finding> getFindings() {
        return Collections.unmodifiableList(new ArrayList<>(findingsHolder.get()));
    }

    public static void clear() {
        findingsHolder.remove();
    }
}
