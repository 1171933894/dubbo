>在 dubbo-remoting-zookeeper 模块，实现了 Dubbo 对 Zookeeper 客户端的封装。在该模块中，抽象了通用的 Zookeeper Client API 接口，实现了两种 Zookeeper Client 库的接入：

>基于 Apache Curator 实现。
<dubbo:registry address="zookeeper://127.0.0.1:2181" client="curator" />

>基于 ZkClient 实现。
<dubbo:registry address="zookeeper://127.0.0.1:2181" client="zkclient" />

>默认不配置 client 的情况下，使用 Curator 。