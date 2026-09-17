package com.akylas.enforcedoze;
interface IPrivilegedService {
    String run(String command) = 1;
    void destroy() = 16777114;
}
