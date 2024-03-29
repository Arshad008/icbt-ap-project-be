package com.abc.lab.abclabsbe.Services;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.abc.lab.abclabsbe.Models.Appointment;
import com.abc.lab.abclabsbe.Models.Test;
import com.abc.lab.abclabsbe.Models.User;
import com.abc.lab.abclabsbe.Repositories.AppointmentRepository;

@Service
public class AppointmentService {
  @Autowired
  private AppointmentRepository appointmentRepository;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private MailService mailService;

  public Object bookAppointment(String requestedDateString, Test test, User user) {
    try {
      Appointment appointment = new Appointment();
      SimpleDateFormat requestedDateFormatter = new SimpleDateFormat("yyyy-MM-dd");
      Date date = requestedDateFormatter.parse(requestedDateString);
      Long totalAppointmentsCount = appointmentRepository.count() + 1;
      String mailSubject = "Appointment booked successfully";

      String mailBody = "Hello " + user.getFirstName() + ",\n\n";
      mailBody += "You appointment for test " + test.getName()
          + " has been booked successfully. please find the appointment details below.\n\n";
      mailBody += "Appointment Number: " + totalAppointmentsCount.toString() + "\n";
      mailBody += "Test Name: " + test.getName() + "\n";
      mailBody += "Test Price: LKR " + test.getPrice().toString() + "\n\n";
      mailBody += "You will receive the appointment date & time shortly.\n\n";
      mailBody += "Thank you!.";

      appointment.setCreatedAt(new Date());
      appointment.setRequestedDate(date);
      appointment.setNumber(totalAppointmentsCount);
      appointment.setStatus("Pending");
      appointment.setTest(test);
      appointment.setUser(user);

      Appointment result = appointmentRepository.insert(appointment);

      mailService.sendMail(user.getEmail(), mailSubject, mailBody);

      return result;
    } catch (ParseException e) {
      return new Object();
    }
  }

  public List<Appointment> getAppointmentsForUser(String userId) {
    Query query = new Query(Criteria.where("userId").is(userId));

    return mongoTemplate.find(query, Appointment.class);
  }

  public List<Appointment> getAppointmentsForAdmin(String dateString) {
    try {
      SimpleDateFormat requestedDateFormatter = new SimpleDateFormat("yyyy-MM-dd");
      Date date = requestedDateFormatter.parse(dateString);

      Calendar startOfDay = Calendar.getInstance();
      startOfDay.setTime(date);
      startOfDay.set(Calendar.HOUR_OF_DAY, 0);
      startOfDay.set(Calendar.MINUTE, 0);
      startOfDay.set(Calendar.SECOND, 0);
      startOfDay.set(Calendar.MILLISECOND, 0);

      Calendar endOfDay = Calendar.getInstance();
      endOfDay.setTime(date);
      endOfDay.set(Calendar.HOUR_OF_DAY, 23);
      endOfDay.set(Calendar.MINUTE, 59);
      endOfDay.set(Calendar.SECOND, 59);
      endOfDay.set(Calendar.MILLISECOND, 999);

      Query query = new Query(Criteria.where("createdAt").gte(startOfDay.getTime()).lte(endOfDay.getTime()));

      return mongoTemplate.find(query, Appointment.class);
    } catch (ParseException e) {
      return new ArrayList<>();
    }
  }

  public Object confirmAppointment(String id, String dateString) {
    try {
      SimpleDateFormat requestedDateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      Date date = requestedDateFormatter.parse(dateString);

      Query query = new Query(Criteria.where("_id").is(id));
      Update update = new Update().set("status", "Confirmed").set("appointmentDate", date);
      FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);

      Appointment result = mongoTemplate.findAndModify(query, update, options, Appointment.class);
      User user = result.getUser();

      String mailSubject = "Appointment Approved";
      String mailBody = "Hello " + user.getFirstName() + ",\n\n";
      mailBody += "You appointment date has been approved.\n\n";
      mailBody += "Please visit our lab on " + new SimpleDateFormat("yyyy-MM-dd hh:mm a").format(date)
          + " in order to provide the sample. Thank you!!";

      mailService.sendMail(user.getEmail(), mailSubject, mailBody);

      return result;
    } catch (Exception e) {
      return new Object();
    }
  }

  public Appointment updateAppointmentDoctor(String id, String doctorName) {
    Query query = new Query(Criteria.where("_id").is(id));
    Update update = new Update().set("doctorName", doctorName);
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);

    return mongoTemplate.findAndModify(query, update, options, Appointment.class);
  }

  public Optional<Appointment> getAppointmentByNumber(Long number) {
    return appointmentRepository.findByNumber(number);
  }

  public Appointment updateStatus(String id, String status) {
    Query query = new Query(Criteria.where("_id").is(id));
    Update update = new Update().set("status", status);
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);

    return mongoTemplate.findAndModify(query, update, options, Appointment.class);
  }

  public Appointment updateTestData(String id, List<Object> testData) {
    Optional<Appointment> appointment = appointmentRepository.findById(id);
    Query query = new Query(Criteria.where("_id").is(id));
    Update update = new Update().set("testData", testData).set("status", "Completed");
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true).upsert(true);

    Appointment result = mongoTemplate.findAndModify(query, update, options, Appointment.class);

    if (appointment.isPresent()) {
      Appointment actualAppointment = appointment.get();

      List<Object> appointmentTestData = actualAppointment.getTestData();

      if (appointmentTestData == null) {
        User user = result.getUser();

        String mailSubject = "You test results are ready.";

        String mailBody = "Hello " + user.getFirstName() + ",\n\n";

        mailBody += "Please find the test result for the appointment number (" + result.getNumber().toString()
            + ") below.\n\n";

        for (Object obj : testData) {
          String testItem = "";

          if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            Integer index = 0;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
              Object value = entry.getValue();

              testItem += value;
              if (index == 0) {
                testItem += ": ";
              }

              index++;
            }
          }

          mailBody += testItem + "\n";
        }

        mailBody += "\nPlease visit our lab to collect the test results. Thank you!";

        mailService.sendMail(user.getEmail(), mailSubject, mailBody);
      }
    }

    return result;
  }

  public List<Appointment> getReportData(String start, String end) {
    try {
      SimpleDateFormat requestedDateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      Date startOfDay = requestedDateFormatter.parse(start);
      Date endOfDay = requestedDateFormatter.parse(end);

      Query query = new Query(
          Criteria.where("createdAt").gte(startOfDay).lte(endOfDay));

      return mongoTemplate.find(query, Appointment.class);
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }
}
