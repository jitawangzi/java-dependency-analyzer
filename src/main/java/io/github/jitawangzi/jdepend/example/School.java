package io.github.jitawangzi.jdepend.example;

public class School {
    private String name;
    private Student[] students = new Student[100];
    private Teacher[] teachers = new Teacher[20];
    private Course[] courses = new Course[50];
    private int studentCount = 0;
    private int teacherCount = 0;
    private int courseCount = 0;

    public School(String name) {
        this.name = name;
    }

    public void addStudent(Student student) {
        if (studentCount < students.length) {
            students[studentCount++] = student;
            System.out.println(student.getName() + " added to " + name);
        } else {
            System.out.println("Cannot add more students");
        }
    }

    public void addTeacher(Teacher teacher) {
        if (teacherCount < teachers.length) {
            teachers[teacherCount++] = teacher;
            System.out.println("Teacher added to " + name);
        } else {
            System.out.println("Cannot add more teachers");
        }
    }

    public void addCourse(Course course) {
        if (courseCount < courses.length) {
            courses[courseCount++] = course;
            System.out.println(course.getCourseName() + " added to " + name);
        } else {
            System.out.println("Cannot add more courses");
        }
    }

    // This method is not called by other classes
    public void organizeEvent(String eventName) {
        System.out.println(name + " is organizing " + eventName);
    }
}

