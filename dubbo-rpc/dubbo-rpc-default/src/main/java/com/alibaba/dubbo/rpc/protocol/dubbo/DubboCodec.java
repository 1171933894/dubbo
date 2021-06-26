/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.rpc.protocol.dubbo;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.io.Bytes;
import com.alibaba.dubbo.common.io.UnsafeByteArrayInputStream;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.serialize.ObjectInput;
import com.alibaba.dubbo.common.serialize.ObjectOutput;
import com.alibaba.dubbo.common.serialize.Serialization;
import com.alibaba.dubbo.common.utils.ReflectUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.Codec2;
import com.alibaba.dubbo.remoting.exchange.Request;
import com.alibaba.dubbo.remoting.exchange.Response;
import com.alibaba.dubbo.remoting.exchange.codec.ExchangeCodec;
import com.alibaba.dubbo.remoting.transport.CodecSupport;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcInvocation;

import java.io.IOException;
import java.io.InputStream;

import static com.alibaba.dubbo.rpc.protocol.dubbo.CallbackServiceCodec.encodeInvocationArgument;

/**
 * Dubbo codec.
 */
public class DubboCodec extends ExchangeCodec implements Codec2 {
    /**
     * 协议名
     */
    public static final String NAME = "dubbo";
    /**
     * 协议版本
     */
    public static final String DUBBO_VERSION = Version.getVersion(DubboCodec.class, Version.getVersion());
    /**
     * 响应 - 异常
     */
    public static final byte RESPONSE_WITH_EXCEPTION = 0;
    /**
     * 响应 - 正常（空返回）
     */
    public static final byte RESPONSE_VALUE = 1;
    /**
     * 响应 - 正常（有返回）
     */
    public static final byte RESPONSE_NULL_VALUE = 2;
    /**
     * 方法参数 - 空（参数）
     */
    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    /**
     * 方法参数 - 空（类型）
     */
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
    private static final Logger log = LoggerFactory.getLogger(DubboCodec.class);

    /**
     * 解码内容体
     */
    protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);// 获得 Serialization 对象
        Serialization s = CodecSupport.getSerialization(channel.getUrl(), proto);
        // get request id.
        long id = Bytes.bytes2long(header, 4);// 获得请求||响应编号
        // 解析响应
        if ((flag & FLAG_REQUEST) == 0) {// 解析响应
            // decode response.
            Response res = new Response(id);
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(Response.HEARTBEAT_EVENT);// 若是心跳事件，进行设置
            }
            // get status.
            byte status = header[3];// 设置状态
            res.setStatus(status);
            // 正常响应状态
            if (status == Response.OK) {
                try {
                    Object data;
                    // 解码心跳事件
                    if (res.isHeartbeat()) {
                        data = decodeHeartbeatData(channel, deserialize(s, channel.getUrl(), is));
                    } else if (res.isEvent()) {// 解码其它事件
                        data = decodeEventData(channel, deserialize(s, channel.getUrl(), is));
                    } else {// 解码普通响应
                        DecodeableRpcResult result;
                        // 在通信框架（例如，Netty）的 IO 线程，解码
                        if (channel.getUrl().getParameter(
                                Constants.DECODE_IN_IO_THREAD_KEY,
                                Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                            result = new DecodeableRpcResult(channel, res, is,
                                    (Invocation) getRequestData(id), proto);
                            result.decode();
                        } else {// 在 Dubbo ThreadPool 线程，解码，使用 DecodeHandler
                            result = new DecodeableRpcResult(channel, res,
                                    new UnsafeByteArrayInputStream(readMessageData(is)),
                                    (Invocation) getRequestData(id), proto);
                        }
                        data = result;// 设置结果
                    }
                    res.setResult(data);
                } catch (Throwable t) {
                    if (log.isWarnEnabled()) {
                        log.warn("Decode response failed: " + t.getMessage(), t);
                    }
                    res.setStatus(Response.CLIENT_ERROR);
                    res.setErrorMessage(StringUtils.toString(t));
                }
            } else {// 异常响应状态
                res.setErrorMessage(deserialize(s, channel.getUrl(), is).readUTF());
            }
            return res;
        } else {
            // decode request.// 解析请求
            Request req = new Request(id);
            req.setVersion("2.0.0");
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);// 是否需要响应
            // 若是心跳事件，进行设置
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(Request.HEARTBEAT_EVENT);
            }
            try {
                Object data;
                if (req.isHeartbeat()) {// 解码心跳事件
                    data = decodeHeartbeatData(channel, deserialize(s, channel.getUrl(), is));
                } else if (req.isEvent()) {// 解码其它事件
                    data = decodeEventData(channel, deserialize(s, channel.getUrl(), is));
                } else {// 解码普通请求
                    // 在通信框架（例如，Netty）的 IO 线程，解码
                    DecodeableRpcInvocation inv;
                    if (channel.getUrl().getParameter(
                            Constants.DECODE_IN_IO_THREAD_KEY,
                            Constants.DEFAULT_DECODE_IN_IO_THREAD)) {
                        inv = new DecodeableRpcInvocation(channel, req, is, proto);
                        inv.decode();
                    } else {
                        inv = new DecodeableRpcInvocation(channel, req,
                                new UnsafeByteArrayInputStream(readMessageData(is)), proto);
                    }
                    data = inv;
                }
                req.setData(data);
            } catch (Throwable t) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode request failed: " + t.getMessage(), t);
                }
                // bad request
                req.setBroken(true);
                req.setData(t);
            }
            return req;
        }
    }

    private ObjectInput deserialize(Serialization serialization, URL url, InputStream is)
            throws IOException {
        return serialization.deserialize(url, is);
    }

    private byte[] readMessageData(InputStream is) throws IOException {
        if (is.available() > 0) {
            byte[] result = new byte[is.available()];
            is.read(result);
            return result;
        }
        return new byte[]{};
    }

    /**
     * 编码请求内容体
     */
    @Override
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data) throws IOException {
        RpcInvocation inv = (RpcInvocation) data;

        // 写入 `dubbo` `path` `version`
        out.writeUTF(inv.getAttachment(Constants.DUBBO_VERSION_KEY, DUBBO_VERSION));
        out.writeUTF(inv.getAttachment(Constants.PATH_KEY));
        out.writeUTF(inv.getAttachment(Constants.VERSION_KEY));

        // 写入方法、方法签名、方法参数集合
        out.writeUTF(inv.getMethodName());
        out.writeUTF(ReflectUtils.getDesc(inv.getParameterTypes()));
        Object[] args = inv.getArguments();
        if (args != null)
            for (int i = 0; i < args.length; i++) {
                out.writeObject(encodeInvocationArgument(channel, inv, i));
            }
        out.writeObject(inv.getAttachments());// 写入隐式传参集合
    }

    /**
     * 编码响应内容体
     */
    @Override
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data) throws IOException {
        Result result = (Result) data;

        Throwable th = result.getException();
        if (th == null) {// 正常
            Object ret = result.getValue();
            if (ret == null) {// 空返回
                out.writeByte(RESPONSE_NULL_VALUE);
            } else {// 有返回
                out.writeByte(RESPONSE_VALUE);
                out.writeObject(ret);
            }
        } else {
            out.writeByte(RESPONSE_WITH_EXCEPTION);
            out.writeObject(th);
        }
    }
}