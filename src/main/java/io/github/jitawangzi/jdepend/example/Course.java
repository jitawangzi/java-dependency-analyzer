package io.github.jitawangzi.jdepend.example;

public class Course {
    private String courseName;
    private String courseCode;
    private int credits;

    public Course(String courseName, String courseCode, int credits) {
        this.courseName = courseName;
        this.courseCode = courseCode;
        this.credits = credits;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getCourseCode() {
        return courseCode;
    }

    // This method is not called by other classes
    public void displaySchedule() {
        System.out.println("Schedule for " + courseName + ":");
        System.out.println("Monday: 9:00 AM - 11:00 AM");
        System.out.println("Wednesday: 9:00 AM - 11:00 AM");
    }

    // This method is not called by other classes
    public void updateCredits(int newCredits) {
        this.credits = newCredits;
        System.out.println(courseName + " credits updated to " + credits);
    }
}

