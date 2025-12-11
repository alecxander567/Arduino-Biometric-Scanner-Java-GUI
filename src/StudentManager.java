import java.util.ArrayList;
import java.io.*;

public class StudentManager {
    private ArrayList<Student> students = new ArrayList<>();
    private final String filename = "students.dat";

    public StudentManager() {
        loadStudents();
    }

    public void addStudent(Student s) {
        students.add(s);
        saveStudents();
    }

    public ArrayList<Student> getStudents() {
        return students;
    }

    public Student findByFingerprintID(int id) {
        for(Student s : students) {
            if(s.getFingerprintID() == id) return s;
        }
        return null;
    }

    private void saveStudents() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(students);
        } catch(Exception e) { e.printStackTrace(); }
    }

    private void loadStudents() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
            students = (ArrayList<Student>) ois.readObject();
        } catch(Exception e) { students = new ArrayList<>(); }
    }
}
