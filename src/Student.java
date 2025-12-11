import java.io.Serializable;

public class Student implements Serializable {
    private String name;
    private String studentID;
    private int fingerprintID;
    private String status;

    public Student(String name, String studentID, int fingerprintID) {
        this.name = name;
        this.studentID = studentID;
        this.fingerprintID = fingerprintID;
        this.status = "Absent";
    }

    // Getters and setters
    public String getName() { return name; }
    public String getStudentID() { return studentID; }
    public int getFingerprintID() { return fingerprintID; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
