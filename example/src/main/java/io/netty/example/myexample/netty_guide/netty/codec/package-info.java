package io.netty.example.myexample.netty_guide.netty.codec;
/*
分隔符和定长解码器的应用：
TCP上层的协议为了消息区分，一般用4种方式，netty也提供了四种对应的抽象：
    1. 固定长度 -> FixedLengthFrameDecoder
    2. 回车换行符作为结束 -> LineBasedFrameDecoder
    3. 特殊分隔符 -> DelimiterBasedFrameDecoder
    4. 消息头定义长度字段（http就是） -> LengthFieldFrameDecoder
 */