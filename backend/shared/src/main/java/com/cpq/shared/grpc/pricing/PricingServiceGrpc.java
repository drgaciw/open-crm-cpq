package com.cpq.shared.grpc.pricing;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Service for pricing calculations
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.59.0)",
    comments = "Source: pricing.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class PricingServiceGrpc {

  private PricingServiceGrpc() {}

  public static final String SERVICE_NAME = "com.cpq.pricing.PricingService";

  // Static method to create a service descriptor.
  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (PricingServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PricingServiceFileDescriptorSupplier())
              .addMethod(getCalculatePriceMethod())
              .build();
        }
      }
    }
    return result;
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  // Method descriptors
  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CalculatePrice",
      requestType = com.cpq.shared.grpc.pricing.CalculatePriceRequest.class,
      responseType = com.cpq.shared.grpc.pricing.CalculatePriceResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.cpq.shared.grpc.pricing.CalculatePriceRequest,
      com.cpq.shared.grpc.pricing.CalculatePriceResponse> getCalculatePriceMethod() {
    io.grpc.MethodDescriptor<com.cpq.shared.grpc.pricing.CalculatePriceRequest, com.cpq.shared.grpc.pricing.CalculatePriceResponse> getCalculatePriceMethod;
    if ((getCalculatePriceMethod = PricingServiceGrpc.getCalculatePriceMethod) == null) {
      synchronized (PricingServiceGrpc.class) {
        if ((getCalculatePriceMethod = PricingServiceGrpc.getCalculatePriceMethod) == null) {
          PricingServiceGrpc.getCalculatePriceMethod = getCalculatePriceMethod =
              io.grpc.MethodDescriptor.<com.cpq.shared.grpc.pricing.CalculatePriceRequest, com.cpq.shared.grpc.pricing.CalculatePriceResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CalculatePrice"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.cpq.shared.grpc.pricing.CalculatePriceRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.cpq.shared.grpc.pricing.CalculatePriceResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PricingServiceMethodDescriptorSupplier("CalculatePrice"))
              .build();
        }
      }
    }
    return getCalculatePriceMethod;
  }

  // Abstract stub class
  public static abstract class PricingServiceImplBase implements io.grpc.BindableService {

    /**
     * <pre>
     * Calculate the price for a configured product
     * </pre>
     */
    public void calculatePrice(com.cpq.shared.grpc.pricing.CalculatePriceRequest request,
        io.grpc.stub.StreamObserver<com.cpq.shared.grpc.pricing.CalculatePriceResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCalculatePriceMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getCalculatePriceMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                com.cpq.shared.grpc.pricing.CalculatePriceRequest,
                com.cpq.shared.grpc.pricing.CalculatePriceResponse>(
                  this, METHODID_CALCULATE_PRICE)))
          .build();
    }
  }

  // Stub classes
  public static final class PricingServiceStub extends io.grpc.stub.AbstractAsyncStub<PricingServiceStub> {
    private PricingServiceStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PricingServiceStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PricingServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Calculate the price for a configured product
     * </pre>
     */
    public void calculatePrice(com.cpq.shared.grpc.pricing.CalculatePriceRequest request,
        io.grpc.stub.StreamObserver<com.cpq.shared.grpc.pricing.CalculatePriceResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCalculatePriceMethod(), getCallOptions()), request, responseObserver);
    }
  }

  public static final class PricingServiceBlockingStub extends io.grpc.stub.AbstractBlockingStub<PricingServiceBlockingStub> {
    private PricingServiceBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PricingServiceBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PricingServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Calculate the price for a configured product
     * </pre>
     */
    public com.cpq.shared.grpc.pricing.CalculatePriceResponse calculatePrice(com.cpq.shared.grpc.pricing.CalculatePriceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCalculatePriceMethod(), getCallOptions(), request);
    }
  }

  public static final class PricingServiceFutureStub extends io.grpc.stub.AbstractFutureStub<PricingServiceFutureStub> {
    private PricingServiceFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PricingServiceFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PricingServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Calculate the price for a configured product
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.cpq.shared.grpc.pricing.CalculatePriceResponse> calculatePrice(
        com.cpq.shared.grpc.pricing.CalculatePriceRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCalculatePriceMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CALCULATE_PRICE = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final PricingServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(PricingServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CALCULATE_PRICE:
          serviceImpl.calculatePrice((com.cpq.shared.grpc.pricing.CalculatePriceRequest) request,
              (io.grpc.stub.StreamObserver<com.cpq.shared.grpc.pricing.CalculatePriceResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class PricingServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PricingServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.cpq.shared.grpc.pricing.PricingProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PricingService");
    }
  }

  private static final class PricingServiceFileDescriptorSupplier
      extends PricingServiceBaseDescriptorSupplier {
    PricingServiceFileDescriptorSupplier() {}
  }

  private static final class PricingServiceMethodDescriptorSupplier
      extends PricingServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    PricingServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (PricingServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PricingServiceFileDescriptorSupplier())
              .addMethod(getCalculatePriceMethod())
              .build();
        }
      }
    }
    return result;
  }
}