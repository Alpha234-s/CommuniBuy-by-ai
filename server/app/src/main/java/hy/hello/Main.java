package hy.hello;

// the main class
public class Main {
    public static void main(String[] args) {
        Hello.print();
        for (String arg : args) {
            System.out.print(arg + "\n");
        }
    }
}