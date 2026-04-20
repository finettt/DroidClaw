package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.filesystem.WorkspaceManager;

/**
 * Fragment that displays a list of available skills.
 */
public class SkillsBrowserFragment extends Fragment {
    private static final String TAG = "SkillsBrowserFragment";

    private RecyclerView skillsList;
    private TextView emptyText;
    private SkillsAdapter skillsAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skills_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        skillsList = view.findViewById(R.id.skillsList);
        emptyText = view.findViewById(R.id.emptyText);

        setupRecyclerView();
        loadSkills();
    }

    private void setupRecyclerView() {
        skillsAdapter = new SkillsAdapter(new ArrayList<>());

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        skillsList.setLayoutManager(layoutManager);
        skillsList.setAdapter(skillsAdapter);

        skillsAdapter.setOnItemClickListener(skill -> {
            Bundle bundle = new Bundle();
            bundle.putString("skill_name", skill.getName());
            bundle.putString("skill_content", skill.getContent());
            Navigation.findNavController(requireView())
                    .navigate(R.id.skillContentFragment, bundle);
        });
    }

    private void loadSkills() {
        skillsAdapter.setLoading(true);


        new Thread(() -> {
            try {
                List<SkillInfo> skills = scanSkills();
                requireActivity().runOnUiThread(() -> {
                    skillsAdapter.setSkills(skills);
                    skillsAdapter.setLoading(false);
                    emptyText.setVisibility(skills.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to load skills", e);
                requireActivity().runOnUiThread(() -> {
                    skillsAdapter.setLoading(false);
                    emptyText.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private List<SkillInfo> scanSkills() throws IOException {
        List<SkillInfo> skills = new ArrayList<>();

        WorkspaceManager workspaceManager = new WorkspaceManager(requireContext());
        String skillsPath = ".agent/skills";

        try {

            List<SkillInfo> workspaceSkills = readSkillsFromDirectory(workspaceManager.getSkillsDirectory());
            skills.addAll(workspaceSkills);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read skills from workspace", e);
        }


        try {
            String[] assetFiles = requireContext().getAssets().list("skills");
            if (assetFiles != null) {
                for (String skillName : assetFiles) {
                    if (!isSkillAlreadyLoaded(skills, skillName)) {
                        SkillInfo skill = readSkillFromAssets(skillName);
                        if (skill != null) {
                            skills.add(skill);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read skills from assets", e);
        }

        return skills;
    }

    private List<SkillInfo> readSkillsFromDirectory(java.io.File skillsDir) throws IOException {
        List<SkillInfo> skills = new ArrayList<>();

        if (!skillsDir.exists() || !skillsDir.isDirectory()) {
            return skills;
        }

        java.io.File[] skillDirs = skillsDir.listFiles();
        if (skillDirs == null) {
            return skills;
        }

        for (java.io.File skillDir : skillDirs) {
            if (skillDir.isDirectory()) {
                SkillInfo skill = readSkillFromDirectory(skillDir);
                if (skill != null) {
                    skills.add(skill);
                }
            }
        }

        return skills;
    }

    private SkillInfo readSkillFromDirectory(java.io.File skillDir) throws IOException {
        java.io.File skillMdFile = new java.io.File(skillDir, "SKILL.md");
        if (!skillMdFile.exists()) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(skillMdFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        SkillInfo skill = parseSkillInfo(skillDir.getName(), content.toString());
        skill.setPath(skillMdFile.getAbsolutePath());
        return skill;
    }

    private SkillInfo readSkillFromAssets(String skillName) throws IOException {
        String skillMdPath = "skills/" + skillName + "/SKILL.md";

        try (java.io.InputStream inputStream = requireContext().getAssets().open(skillMdPath)) {
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }

            return parseSkillInfo(skillName, content.toString());
        } catch (IOException e) {
            Log.w(TAG, "Failed to read skill from assets: " + skillName, e);
            return null;
        }
    }

    private boolean isSkillAlreadyLoaded(List<SkillInfo> skills, String skillName) {
        for (SkillInfo skill : skills) {
            if (skill.getName().equals(skillName)) {
                return true;
            }
        }
        return false;
    }

    private SkillInfo parseSkillInfo(String name, String content) {
        SkillInfo skill = new SkillInfo();
        skill.setName(name);
        skill.setContent(content);


        parseSkillContent(skill, content);

        return skill;
    }

    private void parseSkillContent(SkillInfo skill, String content) {
 from YAML frontmatter or first paragraph
        String description = extractYamlField(content, "description");
        if (description != null && !description.isEmpty()) {
            skill.setDescription(description);
        } else {

            int firstParagraphEnd = content.indexOf("\n\n");
            if (firstParagraphEnd > 0) {
                String firstParagraph = content.substring(0, firstParagraphEnd).trim();

                if (firstParagraph.startsWith("---")) {
                    int frontmatterEnd = firstParagraph.indexOf("---", 3);
                    if (frontmatterEnd > 0) {
                        firstParagraph = firstParagraph.substring(frontmatterEnd + 3).trim();
                    }
                }
                skill.setDescription(firstParagraph);
            }
        }


        skill.setCategory(extractYamlField(content, "category"));
        skill.setAuthor(extractYamlField(content, "author"));
        skill.setVersion(extractYamlField(content, "version"));
        skill.setTags(extractYamlField(content, "tags"));
        skill.setRequiredTools(extractYamlField(content, "required_tools"));
    }

    private String extractYamlField(String content, String fieldName) {

        int frontmatterStart = content.indexOf("---");
        if (frontmatterStart < 0) {
            return null;
        }

        int frontmatterEnd = content.indexOf("---", frontmatterStart + 3);
        if (frontmatterEnd < 0) {
            return null;
        }

        String frontmatter = content.substring(frontmatterStart + 3, frontmatterEnd);
        String pattern = fieldName + ":\\s*(.+?)\n";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(frontmatter);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    static class SkillInfo {
        private String name;
        private String description;
        private String category;
        private String author;
        private String version;
        private String tags;
        private String requiredTools;
        private String content;
        private String path;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getTags() {
            return tags;
        }

        public void setTags(String tags) {
            this.tags = tags;
        }

        public String getRequiredTools() {
            return requiredTools;
        }

        public void setRequiredTools(String requiredTools) {
            this.requiredTools = requiredTools;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    static class SkillsAdapter extends RecyclerView.Adapter<SkillsAdapter.SkillViewHolder> {
        private final List<SkillInfo> skills;
        private boolean isLoading;
        private OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(SkillInfo skill);
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        public SkillsAdapter(List<SkillInfo> skills) {
            this.skills = skills;
            this.isLoading = false;
        }

        public void setSkills(List<SkillInfo> skills) {
            this.skills.clear();
            this.skills.addAll(skills);
            notifyDataSetChanged();
        }

        public void setLoading(boolean loading) {
            this.isLoading = loading;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SkillViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_skill, parent, false);
            return new SkillViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SkillViewHolder holder, int position) {
            SkillInfo skill = skills.get(position);
            holder.bind(skill);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(skill);
                }
            });
        }

        @Override
        public int getItemCount() {
            return skills.size();
        }

        class SkillViewHolder extends RecyclerView.ViewHolder {
            private final TextView skillName;
            private final TextView skillDescription;

            SkillViewHolder(@NonNull View itemView) {
                super(itemView);
                skillName = itemView.findViewById(R.id.skillName);
                skillDescription = itemView.findViewById(R.id.skillDescription);
            }

            void bind(SkillInfo skill) {
                skillName.setText(skill.getName());
                skillDescription.setText(skill.getDescription());
            }
        }
    }
}
