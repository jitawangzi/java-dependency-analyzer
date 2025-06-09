package io.github.jitawangzi.jdepend.example;

public class Teacher {
    private String name;
    private String department;
    private Course[] assignedCourses = new Course[5];
    private int courseCount = 0;
    private GradeBook gradeBook = new GradeBook();

    public Teacher(String name, String department) {
        this.name = name;
        this.department = department;
    }

    public void assignCourse(Course course) {
        if (courseCount < assignedCourses.length) {
            assignedCourses[courseCount++] = course;
            System.out.println(name + " assigned to teach " + course.getCourseName());
        } else {
            System.out.println("Cannot assign more courses");
        }
    }

    public void recordGrade(Student student, Course course, double grade) {
        gradeBook.addGrade(student, course, grade);
        System.out.println("Grade recorded for " + student.getName() + " in " + course.getCourseName());
    }

    // This method is not called by other classes
    public void prepareLesson() {
        System.out.println(name + " is preparing lessons for courses");
    }
}

