package dev.notcustom.app;

interface IShellService {
    String exec(String command) = 2;

    void destroy() = 16777114;
}
