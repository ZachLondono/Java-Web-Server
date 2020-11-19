public enum StatusCode { 
    _200(200, "OK"), 
    _304(304, "Not Modified"),
    _400(400, "Bad Request"),
    _403(403, "Forbidden"), 
    _404(404, "Not Found"), 
    _405(405, "Method Not Allowed"), 
    _408(408, "Request Timeout"), 
    _411(411, "Length Required"),
    _500(500, "Internal Server Error"), 
    _501(501, "Not Implemented"), 
    _503(503, "Service Unavailable"), 
    _505(505, "HTTP Version Not Supported");

    private final String descirption;
    private final int number;

    StatusCode(int number, String description) {
        this.descirption = description;
        this.number = number;
    }

    public String getDescrption() {
        return descirption;
    }
    
    public int getNumber() {
        return number;
    }
    
    public String toString() {
        return number + " " + descirption;
    }

}

