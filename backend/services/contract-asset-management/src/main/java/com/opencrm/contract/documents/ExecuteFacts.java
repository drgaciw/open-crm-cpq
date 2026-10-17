package com.opencrm.contract.documents;

public final class ExecuteFacts {

    public boolean digestMatches = true;
    public boolean executedRevisionAlreadyExists = false;
    public boolean pinsPresent = true;
    public boolean policyPresent = true;
    public boolean totalsPresent = true;

    public static ExecuteFacts passing() {
        return new ExecuteFacts();
    }
}
