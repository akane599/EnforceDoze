package com.akylas.enforcedoze;
public final class ScheduleRules {
    private ScheduleRules() {}
    public static int[] parse(String value) {
        if(value==null || !value.matches("[0-9]{2}:[0-9]{2}-[0-9]{2}:[0-9]{2}")) return null;
        String[] t=value.split("[-:]");
        int sh=Integer.parseInt(t[0]),sm=Integer.parseInt(t[1]),eh=Integer.parseInt(t[2]),em=Integer.parseInt(t[3]);
        if(sh>23 || eh>23 || sm>59 || em>59 || sh==eh && sm==em) return null;
        return new int[]{sh*60+sm,eh*60+em};
    }
    public static boolean contains(String period,int minute) {
        int[] p=parse(period); if(p==null) return false;
        return p[0]<p[1] ? minute>=p[0] && minute<p[1] : minute>=p[0] || minute<p[1];
    }
    public static boolean mayEnter(boolean enabled,boolean screenOn,boolean inSchedule,boolean charging,boolean pauseCharging,boolean call) {
        return enabled && !screenOn && inSchedule && !(charging && pauseCharging) && !call;
    }
}
