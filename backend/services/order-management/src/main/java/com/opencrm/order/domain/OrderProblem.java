package com.opencrm.order.domain;
public class OrderProblem extends RuntimeException {
 private final int status;
 public OrderProblem(int status,String message) { super(message);this.status=status; }
 public int status() { return status; }
 public static OrderProblem invalid(String message) { return new OrderProblem(422,message); }
 public static OrderProblem conflict(String message) { return new OrderProblem(409,message); }
 public static OrderProblem missing() { return new OrderProblem(404,"Order or source not found"); }
}
