package io.finett.droidclaw.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.UUID;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.CronJob;

public class CronJobEditorDialog extends DialogFragment {

    private static final String ARG_JOB_ID = "job_id";
    private static final String ARG_JOB_NAME = "job_name";
    private static final String ARG_JOB_PROMPT = "job_prompt";
    private static final String ARG_JOB_SCHEDULE = "job_schedule";

    private TextInputEditText editJobName;
    private TextInputEditText editJobPrompt;
    private TextInputLayout layoutJobName;
    private TextInputLayout layoutJobPrompt;
    private RadioGroup radioScheduleType;
    private RadioButton radioDaily;
    private RadioButton radioWeekly;
    private RadioButton radioHourly;
    private RadioButton radioCustom;
    private LinearLayout layoutDailyTime;
    private LinearLayout layoutWeeklyOptions;
    private LinearLayout layoutCustomInterval;
    private TextInputEditText editDailyTime;
    private Spinner spinnerWeeklyDay;
    private TextInputEditText editWeeklyTime;
    private TextInputEditText editCustomValue;
    private Spinner spinnerCustomUnit;
    private MaterialButton buttonSave;
    private MaterialButton buttonCancel;

    private CronJob jobToEdit;
    private String originalSchedule;

    public static CronJobEditorDialog newInstance(CronJob job) {
        CronJobEditorDialog dialog = new CronJobEditorDialog();
        Bundle args = new Bundle();
        if (job != null) {
            args.putString(ARG_JOB_ID, job.getId());
            args.putString(ARG_JOB_NAME, job.getName());
            args.putString(ARG_JOB_PROMPT, job.getPrompt());
            args.putString(ARG_JOB_SCHEDULE, job.getSchedule());
        }
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_cron_job_editor, null);

        initializeViews(view);
        setupSpinners();
        setupScheduleRadioGroup();
        loadDataFromArguments();
        setupButtons();

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(jobToEdit != null ? R.string.cron_job_edit : R.string.add_cron_job)
                .setView(view)
                .create();
    }

    private void initializeViews(View view) {
        editJobName = view.findViewById(R.id.edit_job_name);
        editJobPrompt = view.findViewById(R.id.edit_job_prompt);
        layoutJobName = view.findViewById(R.id.layout_job_name);
        layoutJobPrompt = view.findViewById(R.id.layout_job_prompt);
        radioScheduleType = view.findViewById(R.id.radio_schedule_type);
        radioDaily = view.findViewById(R.id.radio_daily);
        radioWeekly = view.findViewById(R.id.radio_weekly);
        radioHourly = view.findViewById(R.id.radio_hourly);
        radioCustom = view.findViewById(R.id.radio_custom);
        layoutDailyTime = view.findViewById(R.id.layout_daily_time);
        layoutWeeklyOptions = view.findViewById(R.id.layout_weekly_options);
        layoutCustomInterval = view.findViewById(R.id.layout_custom_interval);
        editDailyTime = view.findViewById(R.id.edit_daily_time);
        spinnerWeeklyDay = view.findViewById(R.id.spinner_weekly_day);
        editWeeklyTime = view.findViewById(R.id.edit_weekly_time);
        editCustomValue = view.findViewById(R.id.edit_custom_value);
        spinnerCustomUnit = view.findViewById(R.id.spinner_custom_unit);
        buttonSave = view.findViewById(R.id.button_save);
        buttonCancel = view.findViewById(R.id.button_cancel);
    }

    private void setupSpinners() {
        // Weekly day spinner
        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, days);
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWeeklyDay.setAdapter(dayAdapter);

        // Custom unit spinner
        String[] units = {"Minutes", "Hours", "Days"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCustomUnit.setAdapter(unitAdapter);
    }

    private void setupScheduleRadioGroup() {
        radioScheduleType.setOnCheckedChangeListener((group, checkedId) -> {
            layoutDailyTime.setVisibility(View.GONE);
            layoutWeeklyOptions.setVisibility(View.GONE);
            layoutCustomInterval.setVisibility(View.GONE);

            if (checkedId == R.id.radio_daily) {
                layoutDailyTime.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radio_weekly) {
                layoutWeeklyOptions.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radio_custom) {
                layoutCustomInterval.setVisibility(View.VISIBLE);
            }
            // hourly needs no additional UI
        });

        // Default to daily
        radioDaily.setChecked(true);
    }

    private void loadDataFromArguments() {
        Bundle args = getArguments();
        if (args != null) {
            String jobId = args.getString(ARG_JOB_ID);
            if (jobId != null && !jobId.isEmpty()) {
                jobToEdit = new CronJob();
                jobToEdit.setId(jobId);
                jobToEdit.setName(args.getString(ARG_JOB_NAME, ""));
                jobToEdit.setPrompt(args.getString(ARG_JOB_PROMPT, ""));
                originalSchedule = args.getString(ARG_JOB_SCHEDULE, "");

                editJobName.setText(jobToEdit.getName());
                editJobPrompt.setText(jobToEdit.getPrompt());

                // Parse schedule to set radio buttons
                parseAndSetSchedule(originalSchedule);
            }
        }
    }

    private void parseAndSetSchedule(String schedule) {
        if (schedule == null || schedule.isEmpty()) {
            radioDaily.setChecked(true);
            return;
        }

        String normalized = schedule.trim().toLowerCase();

        if (normalized.equals("hourly")) {
            radioHourly.setChecked(true);
        } else if (normalized.equals("daily") || normalized.startsWith("daily@")) {
            radioDaily.setChecked(true);
            if (normalized.startsWith("daily@")) {
                String time = schedule.substring(6);
                editDailyTime.setText(time);
            }
        } else if (normalized.equals("weekly") || normalized.startsWith("weekly@")) {
            radioWeekly.setChecked(true);
            if (normalized.startsWith("weekly@")) {
                String[] parts = schedule.substring(7).split("@");
                if (parts.length == 2) {
                    String day = parts[0].toLowerCase();
                    String time = parts[1];
                    editWeeklyTime.setText(time);

                    // Set day in spinner
                    String[] days = {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"};
                    for (int i = 0; i < days.length; i++) {
                        if (days[i].equals(day)) {
                            spinnerWeeklyDay.setSelection(i);
                            break;
                        }
                    }
                }
            }
        } else {
            // Try parsing as custom interval
            try {
                long ms = Long.parseLong(normalized);
                long minutes = ms / (1000 * 60);
                long hours = minutes / 60;
                long days = hours / 24;

                if (days > 0 && days < 7) {
                    radioCustom.setChecked(true);
                    editCustomValue.setText(String.valueOf(days));
                    spinnerCustomUnit.setSelection(2); // Days
                } else if (hours > 0) {
                    radioCustom.setChecked(true);
                    editCustomValue.setText(String.valueOf(hours));
                    spinnerCustomUnit.setSelection(1); // Hours
                } else if (minutes > 0) {
                    radioCustom.setChecked(true);
                    editCustomValue.setText(String.valueOf(minutes));
                    spinnerCustomUnit.setSelection(0); // Minutes
                } else {
                    radioDaily.setChecked(true);
                }
            } catch (NumberFormatException e) {
                radioDaily.setChecked(true);
            }
        }
    }

    private void setupButtons() {
        buttonCancel.setOnClickListener(v -> dismiss());
        buttonSave.setOnClickListener(v -> saveJob());
    }

    private void saveJob() {
        // Validate
        String name = editJobName.getText() != null ? editJobName.getText().toString().trim() : "";
        String prompt = editJobPrompt.getText() != null ? editJobPrompt.getText().toString().trim() : "";

        boolean hasError = false;

        if (name.isEmpty()) {
            layoutJobName.setError(getString(R.string.cron_validation_name_required));
            hasError = true;
        } else {
            layoutJobName.setError(null);
        }

        if (prompt.isEmpty()) {
            layoutJobPrompt.setError(getString(R.string.cron_validation_prompt_required));
            hasError = true;
        } else {
            layoutJobPrompt.setError(null);
        }

        if (hasError) return;

        // Build schedule string
        String schedule = buildScheduleString();

        // Create or update job
        CronJob job;
        if (jobToEdit != null) {
            job = jobToEdit;
        } else {
            job = new CronJob();
            job.setId(UUID.randomUUID().toString());
        }

        job.setName(name);
        job.setPrompt(prompt);
        job.setSchedule(schedule);

        // Notify parent fragment
        if (getTargetFragment() instanceof OnCronJobSavedListener) {
            ((OnCronJobSavedListener) getTargetFragment()).onCronJobSaved(job);
        }

        dismiss();
    }

    private String buildScheduleString() {
        int checkedId = radioScheduleType.getCheckedRadioButtonId();

        if (checkedId == R.id.radio_hourly) {
            return "hourly";
        } else if (checkedId == R.id.radio_daily) {
            String time = editDailyTime.getText() != null ? editDailyTime.getText().toString().trim() : "09:00";
            return "daily@" + time;
        } else if (checkedId == R.id.radio_weekly) {
            String day = spinnerWeeklyDay.getSelectedItem().toString().toLowerCase();
            String time = editWeeklyTime.getText() != null ? editWeeklyTime.getText().toString().trim() : "09:00";
            return "weekly@" + day + "@" + time;
        } else if (checkedId == R.id.radio_custom) {
            String value = editCustomValue.getText() != null ? editCustomValue.getText().toString().trim() : "1";
            String unit = spinnerCustomUnit.getSelectedItem().toString().toLowerCase();
            return "every_" + value + "_" + unit;
        }

        return "daily@09:00"; // Default
    }

    public interface OnCronJobSavedListener {
        void onCronJobSaved(CronJob job);
    }
}
