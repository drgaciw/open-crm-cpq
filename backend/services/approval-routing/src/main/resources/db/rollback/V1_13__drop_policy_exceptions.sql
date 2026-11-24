-- Manual rollback of V1_13__policy_exceptions.sql. Run with search_path = approval.
SET search_path TO approval;

DROP TABLE IF EXISTS policy_exceptions;
