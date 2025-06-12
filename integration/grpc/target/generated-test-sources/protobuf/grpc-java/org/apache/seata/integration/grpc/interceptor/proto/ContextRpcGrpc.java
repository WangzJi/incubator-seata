package org.apache.seata.integration.grpc.interceptor.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.55.1)",
    comments = "Source: contextTest.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ContextRpcGrpc {

  private ContextRpcGrpc() {}

  public static final String SERVICE_NAME = "ContextRpc";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<org.apache.seata.integration.grpc.interceptor.proto.Request,
      org.apache.seata.integration.grpc.interceptor.proto.Response> getContextRpcMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ContextRpc",
      requestType = org.apache.seata.integration.grpc.interceptor.proto.Request.class,
      responseType = org.apache.seata.integration.grpc.interceptor.proto.Response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.apache.seata.integration.grpc.interceptor.proto.Request,
      org.apache.seata.integration.grpc.interceptor.proto.Response> getContextRpcMethod() {
    io.grpc.MethodDescriptor<org.apache.seata.integration.grpc.interceptor.proto.Request, org.apache.seata.integration.grpc.interceptor.proto.Response> getContextRpcMethod;
    if ((getContextRpcMethod = ContextRpcGrpc.getContextRpcMethod) == null) {
      synchronized (ContextRpcGrpc.class) {
        if ((getContextRpcMethod = ContextRpcGrpc.getContextRpcMethod) == null) {
          ContextRpcGrpc.getContextRpcMethod = getContextRpcMethod =
              io.grpc.MethodDescriptor.<org.apache.seata.integration.grpc.interceptor.proto.Request, org.apache.seata.integration.grpc.interceptor.proto.Response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ContextRpc"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.seata.integration.grpc.interceptor.proto.Request.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.apache.seata.integration.grpc.interceptor.proto.Response.getDefaultInstance()))
              .setSchemaDescriptor(new ContextRpcMethodDescriptorSupplier("ContextRpc"))
              .build();
        }
      }
    }
    return getContextRpcMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ContextRpcStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ContextRpcStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ContextRpcStub>() {
        @java.lang.Override
        public ContextRpcStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ContextRpcStub(channel, callOptions);
        }
      };
    return ContextRpcStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ContextRpcBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ContextRpcBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ContextRpcBlockingStub>() {
        @java.lang.Override
        public ContextRpcBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ContextRpcBlockingStub(channel, callOptions);
        }
      };
    return ContextRpcBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ContextRpcFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ContextRpcFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ContextRpcFutureStub>() {
        @java.lang.Override
        public ContextRpcFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ContextRpcFutureStub(channel, callOptions);
        }
      };
    return ContextRpcFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void contextRpc(org.apache.seata.integration.grpc.interceptor.proto.Request request,
        io.grpc.stub.StreamObserver<org.apache.seata.integration.grpc.interceptor.proto.Response> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getContextRpcMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ContextRpc.
   */
  public static abstract class ContextRpcImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ContextRpcGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ContextRpc.
   */
  public static final class ContextRpcStub
      extends io.grpc.stub.AbstractAsyncStub<ContextRpcStub> {
    private ContextRpcStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ContextRpcStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ContextRpcStub(channel, callOptions);
    }

    /**
     */
    public void contextRpc(org.apache.seata.integration.grpc.interceptor.proto.Request request,
        io.grpc.stub.StreamObserver<org.apache.seata.integration.grpc.interceptor.proto.Response> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getContextRpcMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ContextRpc.
   */
  public static final class ContextRpcBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ContextRpcBlockingStub> {
    private ContextRpcBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ContextRpcBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ContextRpcBlockingStub(channel, callOptions);
    }

    /**
     */
    public org.apache.seata.integration.grpc.interceptor.proto.Response contextRpc(org.apache.seata.integration.grpc.interceptor.proto.Request request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getContextRpcMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ContextRpc.
   */
  public static final class ContextRpcFutureStub
      extends io.grpc.stub.AbstractFutureStub<ContextRpcFutureStub> {
    private ContextRpcFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ContextRpcFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ContextRpcFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.apache.seata.integration.grpc.interceptor.proto.Response> contextRpc(
        org.apache.seata.integration.grpc.interceptor.proto.Request request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getContextRpcMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CONTEXT_RPC = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CONTEXT_RPC:
          serviceImpl.contextRpc((org.apache.seata.integration.grpc.interceptor.proto.Request) request,
              (io.grpc.stub.StreamObserver<org.apache.seata.integration.grpc.interceptor.proto.Response>) responseObserver);
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

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getContextRpcMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.apache.seata.integration.grpc.interceptor.proto.Request,
              org.apache.seata.integration.grpc.interceptor.proto.Response>(
                service, METHODID_CONTEXT_RPC)))
        .build();
  }

  private static abstract class ContextRpcBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ContextRpcBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return org.apache.seata.integration.grpc.interceptor.proto.ContextRpcTest.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ContextRpc");
    }
  }

  private static final class ContextRpcFileDescriptorSupplier
      extends ContextRpcBaseDescriptorSupplier {
    ContextRpcFileDescriptorSupplier() {}
  }

  private static final class ContextRpcMethodDescriptorSupplier
      extends ContextRpcBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    ContextRpcMethodDescriptorSupplier(String methodName) {
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
      synchronized (ContextRpcGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ContextRpcFileDescriptorSupplier())
              .addMethod(getContextRpcMethod())
              .build();
        }
      }
    }
    return result;
  }
}
