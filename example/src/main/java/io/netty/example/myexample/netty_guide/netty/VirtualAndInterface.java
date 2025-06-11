package io.netty.example.myexample.netty_guide.netty;

/**
 * 在极端热点下invoke_interface和invoke_virtual有性能差距，
 * 超多态情况下invoke_interface难以做方法内联
 */
public class VirtualAndInterface {
    public static void main(String[] args) {
        new VirtualAndInterface().test();
    }
    void test() {
        Inter clazz = new Clazz();  // invoke_interface
        clazz.help();
        Clazzz clazzz = new Clazzz();   // invoke_virtual
        clazzz.help();
        ((Clazz) clazz).help(); // invoke_virtual
    }
}
interface Inter {
    void help();
}
class Clazz implements Inter {
    @Override
    public void help() {
        System.out.println("help");
    }
}
class Clazzz implements Inter {
    @Override
    public void help() {
        System.out.println("help");
    }
}
