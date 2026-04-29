package io.finett.droidclaw.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.ChatRepository;
import io.finett.droidclaw.service.ChatContinuationService;
import io.finett.droidclaw.util.MarkdownRenderer;

/**
 * ZenResultFragment provides a distraction-free, focused view of task results.
 * Features:
 * - Clean markdown rendering
 * - Floating "Chat about this" button
 * - Swipe-to-dismiss gesture
 * - Share result action
 * - Material Design 3 styling
 */
public class ZenResultFragment extends Fragment {
    private static final String TAG = "ZenResultFragment";
    public static final String ARG_TASK_RESULT = "task_result";

    private TextView resultTitle;
    private TextView resultTimestamp;
    private TextView resultContent;
    private ExtendedFloatingActionButton fabChatAboutThis;
    private FloatingActionButton fabShare;

    private TaskResult taskResult;
    private ChatContinuationService continuationService;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        continuationService = new ChatContinuationService(requireContext());


        if (getArguments() != null) {
            taskResult = (TaskResult) getArguments().getSerializable(ARG_TASK_RESULT);
        }

        if (taskResult == null) {
            Toast.makeText(requireContext(), "No task result provided", Toast.LENGTH_SHORT).show();
            requireActivity().finish();
            return;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_zen_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        populateData();
        setupClickListeners();
    }

    private void initViews(View view) {
        resultTitle = view.findViewById(R.id.resultTitle);
        resultTimestamp = view.findViewById(R.id.resultTimestamp);
        resultContent = view.findViewById(R.id.resultContent);
        fabChatAboutThis = view.findViewById(R.id.fabChatAboutThis);
        fabShare = view.findViewById(R.id.fabShare);
    }

    private void populateData() {
        if (taskResult == null) return;


        String typeLabel = getTaskTypeLabel(taskResult.getType());
        resultTitle.setText(typeLabel + " Result");


        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault());
        String formattedDate = sdf.format(new Date(taskResult.getTimestamp()));
        resultTimestamp.setText(formattedDate);


        String content = taskResult.getContent();
        if (content != null && !content.isEmpty()) {
            if (MarkdownRenderer.containsMarkdown(content)) {
                MarkdownRenderer.render(requireContext(), resultContent, content);
            } else {
                resultContent.setText(content);
            }
        } else {
            resultContent.setText("No content available");
        }
    }

    private void setupClickListeners() {

        fabChatAboutThis.setOnClickListener(v -> showChatContinuationDialog());


        fabShare.setOnClickListener(v -> shareResult());
    }

    private void showChatContinuationDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.chat_continuation_picker_title)
                .setItems(new String[]{
                        getString(R.string.chat_continuation_new),
                        getString(R.string.chat_continuation_existing)
                }, (dialog, which) -> {
                    if (which == 0) {
                        continueInNewChat();
                    } else {
                        continueInExistingChat();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void continueInNewChat() {
        if (!(requireActivity() instanceof MainActivity)) {
            return;
        }


        ChatSession newSession = continuationService.continueInNewChat(taskResult);

        MainActivity mainActivity = (MainActivity) requireActivity();
        mainActivity.addChatSessionToList(newSession);


        Bundle args = new Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, newSession.getId());
        Navigation.findNavController(requireView()).navigate(R.id.chatFragment, args);
    }

    private void continueInExistingChat() {
        if (!(requireActivity() instanceof MainActivity)) {
            return;
        }

        List<ChatSession> visibleSessions = new ChatRepository(requireContext()).getVisibleSessions();
        if (visibleSessions.isEmpty()) {
            Toast.makeText(requireContext(), "No existing chats available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (visibleSessions.size() == 1) {
            continueInSelectedChat(visibleSessions.get(0));
            return;
        }

        CharSequence[] sessionTitles = new CharSequence[visibleSessions.size()];
        for (int i = 0; i < visibleSessions.size(); i++) {
            ChatSession session = visibleSessions.get(i);
            String title = session.getTitle();
            sessionTitles[i] = (title == null || title.trim().isEmpty())
                    ? getString(R.string.new_chat)
                    : title;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.chat_continuation_existing)
                .setItems(sessionTitles, (dialog, which) -> continueInSelectedChat(visibleSessions.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void continueInSelectedChat(ChatSession session) {
        if (session == null) {
            return;
        }

        continuationService.continueInExistingChat(taskResult, session.getId());

        Bundle args = new Bundle();
        args.putString(ChatFragment.ARG_SESSION_ID, session.getId());
        Navigation.findNavController(requireView()).navigate(R.id.chatFragment, args);
    }

    private void shareResult() {
        if (taskResult == null) return;

        String typeLabel = getTaskTypeLabel(taskResult.getType());
        String shareText = typeLabel + " Result\n\n" + taskResult.getContent();

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.result_share_title));
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);

        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_result)));
    }

    private String getTaskTypeLabel(int type) {
        switch (type) {
            case TaskResult.TYPE_HEARTBEAT:
                return getString(R.string.result_type_heartbeat);
            case TaskResult.TYPE_CRON_JOB:
                return getString(R.string.result_type_cron);
            case TaskResult.TYPE_MANUAL:
                return getString(R.string.result_type_manual);
            default:
                return "Task";
        }
    }
}
