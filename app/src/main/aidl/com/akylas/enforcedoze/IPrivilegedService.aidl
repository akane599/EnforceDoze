package com.akylas.enforcedoze;

interface IPrivilegedService {
    String[] execute(String command) = 0;
    void destroy() = 16777114;
}
