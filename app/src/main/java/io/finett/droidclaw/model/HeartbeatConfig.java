package io.finett.droidclaw.model;

/**
 * Configuration for the Heartbeat feature.
 * 
 * Heartbeat runs periodic checks in the main chat session,
 * reading HEARTBEAT.md from workspace and alerting if needed.
 */
public class HeartbeatConfig {
    private boolean enabled = true;
    private long intervalMinutes = 30;  // Default 30 minutes
    
    // UI behavior
    private boolean showOkMessages = false;      // Don't show HEARTBEAT_OK in chat
    private boolean showAlerts = true;           // Show alert messages
    private boolean sendNotifications = true;    // Push notifications for alerts
    private int ackMaxChars = 300;               // Max chars after HEARTBEAT_OK to still be silent
    
    // Active hours (optional - restrict to daytime)
    private String activeHoursStart;  // e.g., "08:00" (24-hour format)
    private String activeHoursEnd;    // e.g., "22:00"
    private boolean respectActiveHours = false;
    
    // Execution constraints
    private boolean requireNetwork = false;
    private boolean batteryNotLow = true;
    
    // Last execution tracking
    private long lastRunAt = 0;
    private String lastStatus = "";  // "ok", "alert", "error"
    
    public HeartbeatConfig() {
    }
    
    // Getters and setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public long getIntervalMinutes() {
        return intervalMinutes;
    }
    
    public void setIntervalMinutes(long intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }
    
    public boolean isShowOkMessages() {
        return showOkMessages;
    }
    
    public void setShowOkMessages(boolean showOkMessages) {
        this.showOkMessages = showOkMessages;
    }
    
    public boolean isShowAlerts() {
        return showAlerts;
    }
    
    public void setShowAlerts(boolean showAlerts) {
        this.showAlerts = showAlerts;
    }
    
    public boolean isSendNotifications() {
        return sendNotifications;
    }
    
    public void setSendNotifications(boolean sendNotifications) {
        this.sendNotifications = sendNotifications;
    }
    
    public int getAckMaxChars() {
        return ackMaxChars;
    }
    
    public void setAckMaxChars(int ackMaxChars) {
        this.ackMaxChars = ackMaxChars;
    }
    
    public String getActiveHoursStart() {
        return activeHoursStart;
    }
    
    public void setActiveHoursStart(String activeHoursStart) {
        this.activeHoursStart = activeHoursStart;
    }
    
    public String getActiveHoursEnd() {
        return activeHoursEnd;
    }
    
    public void setActiveHoursEnd(String activeHoursEnd) {
        this.activeHoursEnd = activeHoursEnd;
    }
    
    public boolean isRespectActiveHours() {
        return respectActiveHours;
    }
    
    public void setRespectActiveHours(boolean respectActiveHours) {
        this.respectActiveHours = respectActiveHours;
    }
    
    public boolean isRequireNetwork() {
        return requireNetwork;
    }
    
    public void setRequireNetwork(boolean requireNetwork) {
        this.requireNetwork = requireNetwork;
    }
    
    public boolean isBatteryNotLow() {
        return batteryNotLow;
    }
    
    public void setBatteryNotLow(boolean batteryNotLow) {
        this.batteryNotLow = batteryNotLow;
    }
    
    public long getLastRunAt() {
        return lastRunAt;
    }
    
    public void setLastRunAt(long lastRunAt) {
        this.lastRunAt = lastRunAt;
    }
    
    public String getLastStatus() {
        return lastStatus;
    }
    
    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }
    
    /**
     * Check if current time is within active hours.
     * 
     * @param currentHour Current hour in 24-hour format (0-23)
     * @return true if within active hours or active hours not configured
     */
    public boolean isWithinActiveHours(int currentHour) {
        if (!respectActiveHours || activeHoursStart == null || activeHoursEnd == null) {
            return true;  // Active hours not configured, always run
        }
        
        try {
            int startHour = Integer.parseInt(activeHoursStart.split(":")[0]);
            int endHour = Integer.parseInt(activeHoursEnd.split(":")[0]);
            
            // Handle wraparound (e.g., 22:00 to 08:00)
            if (startHour <= endHour) {
                return currentHour >= startHour && currentHour < endHour;
            } else {
                return currentHour >= startHour || currentHour < endHour;
            }
        } catch (Exception e) {
            return true;  // Parse error, allow execution
        }
    }
}