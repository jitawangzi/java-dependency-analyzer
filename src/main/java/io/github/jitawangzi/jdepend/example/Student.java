package io.github.jitawangzi.jdepend.example;

public class Student {
    private String name;
    private int id;
    private Course[] enrolledCourses = new Course[10];
    private int courseCount = 0;

    public Student(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public void enrollCourse(Course course) {
        if (courseCount < enrolledCourses.length) {
            enrolledCourses[courseCount++] = course;
            System.out.println(name + " enrolled in " + course.getCourseName());
        } else {
            System.out.println("Cannot enroll in more courses");
        }
    }

    public void displayInfo() {
        System.out.println("Student ID: " + id + ", Name: " + name);
        System.out.println("Enrolled Courses:");
        for (int i = 0; i < courseCount; i++) {
            System.out.println("- " + enrolledCourses[i].getCourseName());
        }
    }

    // This method is not called by other classes
    public void study() {
        System.out.println(name + " is studying hard");
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }
}

