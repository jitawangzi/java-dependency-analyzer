package io.github.jitawangzi.jdepend.example;

public class GradeBook {
    private static class GradeEntry {
        Student student;
        Course course;
        double grade;

        GradeEntry(Student student, Course course, double grade) {
            this.student = student;
            this.course = course;
            this.grade = grade;
        }
    }

    private GradeEntry[] entries = new GradeEntry[1000];
    private int entryCount = 0;

    public void addGrade(Student student, Course course, double grade) {
        if (entryCount < entries.length) {
            entries[entryCount++] = new GradeEntry(student, course, grade);
        } else {
            System.out.println("Grade book is full");
        }
    }

    public void displayStudentGrades(Student student) {
        System.out.println("Grades for " + student.getName() + ":");
        for (int i = 0; i < entryCount; i++) {
            if (entries[i].student.getId() == student.getId()) {
                System.out.println(entries[i].course.getCourseName() + ": " + entries[i].grade);
            }
        }
    }

    // This method is not called by other classes
    public double calculateAverageGrade(Course course) {
        double sum = 0;
        int count = 0;
        for (int i = 0; i < entryCount; i++) {
            if (entries[i].course.getCourseCode().equals(course.getCourseCode())) {
                sum += entries[i].grade;
                count++;
            }
        }
        return count > 0 ? sum / count : 0;
    }
}
