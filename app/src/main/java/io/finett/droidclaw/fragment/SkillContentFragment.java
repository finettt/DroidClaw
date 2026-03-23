package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.finett.droidclaw.R;

/**
 * Fragment that displays detailed information about a skill.
 */
public class SkillContentFragment extends Fragment {
    private static final String ARG_SKILL_NAME = "skill_name";
    private static final String ARG_SKILL_CONTENT = "skill_content";

    private TextView skillName;
    private TextView skillVersion;
    private TextView skillCategory;
    private TextView skillDescription;
    private TextView skillCapabilities;
    private TextView skillGuidelines;
    private TextView skillExamples;
    private TextView skillLimitations;
    private TextView skillRequiredTools;
    private TextView skillTags;
    private TextView skillAuthor;

    public static SkillContentFragment newInstance(String skillName, String skillContent) {
        SkillContentFragment fragment = new SkillContentFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SKILL_NAME, skillName);
        args.putString(ARG_SKILL_CONTENT, skillContent);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_skill_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        skillName = view.findViewById(R.id.skillName);
        skillVersion = view.findViewById(R.id.skillVersion);
        skillCategory = view.findViewById(R.id.skillCategory);
        skillDescription = view.findViewById(R.id.skillDescription);
        skillCapabilities = view.findViewById(R.id.skillCapabilities);
        skillGuidelines = view.findViewById(R.id.skillGuidelines);
        skillExamples = view.findViewById(R.id.skillExamples);
        skillLimitations = view.findViewById(R.id.skillLimitations);
        skillRequiredTools = view.findViewById(R.id.skillRequiredTools);
        skillTags = view.findViewById(R.id.skillTags);
        skillAuthor = view.findViewById(R.id.skillAuthor);

        if (getArguments() != null) {
            String skillNameArg = getArguments().getString(ARG_SKILL_NAME);
            String skillContentArg = getArguments().getString(ARG_SKILL_CONTENT);

            if (skillNameArg != null && skillContentArg != null) {
                SkillInfo skill = parseSkillInfo(skillNameArg, skillContentArg);
                displaySkill(skill);
            }
        }
    }

    private SkillInfo parseSkillInfo(String name, String content) {
        SkillInfo skill = new SkillInfo();
        skill.setName(name);
        skill.setContent(content);
        parseSkillContent(skill, content);
        return skill;
    }

    private void parseSkillContent(SkillInfo skill, String content) {
        // Extract description
        String description = extractYamlField(content, "description");
        if (description != null && !description.isEmpty()) {
            skill.setDescription(description);
        }

        // Extract capabilities section
        String capabilities = extractSection(content, "## Capabilities", "## Guidelines");
        if (capabilities != null) {
            skill.setCapabilities(capabilities);
        }

        // Extract guidelines
        String guidelines = extractSection(content, "## Guidelines", "## Example");
        if (guidelines != null) {
            skill.setGuidelines(guidelines);
        }

        // Extract examples
        String examples = extractSection(content, "## Example Usage", "## Limitations");
        if (examples != null) {
            skill.setExamples(examples);
        }

        // Extract limitations
        String limitations = extractSection(content, "## Limitations", null);
        if (limitations != null) {
            skill.setLimitations(limitations);
        }

        // Extract other YAML fields
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

    private String extractSection(String content, String startMarker, String endMarker) {
        int startIndex = content.indexOf(startMarker);
        if (startIndex < 0) {
            return null;
        }

        int endIndex;
        if (endMarker == null) {
            endIndex = content.length();
        } else {
            endIndex = content.indexOf(endMarker, startIndex);
            if (endIndex < 0) {
                endIndex = content.length();
            }
        }

        return content.substring(startIndex, endIndex).trim();
    }

    private void displaySkill(SkillInfo skill) {
        skillName.setText(skill.getName());

        // Version
        if (skill.getVersion() != null && !skill.getVersion().isEmpty()) {
            skillVersion.setText("v" + skill.getVersion());
            skillVersion.setVisibility(View.VISIBLE);
        } else {
            skillVersion.setVisibility(View.GONE);
        }

        // Category
        if (skill.getCategory() != null && !skill.getCategory().isEmpty()) {
            skillCategory.setText(skill.getCategory());
            skillCategory.setVisibility(View.VISIBLE);
        } else {
            skillCategory.setVisibility(View.GONE);
        }

        // Description
        if (skill.getDescription() != null && !skill.getDescription().isEmpty()) {
            skillDescription.setText(skill.getDescription());
            skillDescription.setVisibility(View.VISIBLE);
        } else {
            skillDescription.setVisibility(View.GONE);
        }

        // Capabilities
        if (skill.getCapabilities() != null && !skill.getCapabilities().isEmpty()) {
            skillCapabilities.setText(skill.getCapabilities());
            skillCapabilities.setVisibility(View.VISIBLE);
        } else {
            skillCapabilities.setVisibility(View.GONE);
        }

        // Guidelines
        if (skill.getGuidelines() != null && !skill.getGuidelines().isEmpty()) {
            skillGuidelines.setText(skill.getGuidelines());
            skillGuidelines.setVisibility(View.VISIBLE);
        } else {
            skillGuidelines.setVisibility(View.GONE);
        }

        // Examples
        if (skill.getExamples() != null && !skill.getExamples().isEmpty()) {
            skillExamples.setText(skill.getExamples());
            skillExamples.setVisibility(View.VISIBLE);
        } else {
            skillExamples.setVisibility(View.GONE);
        }

        // Limitations
        if (skill.getLimitations() != null && !skill.getLimitations().isEmpty()) {
            skillLimitations.setText(skill.getLimitations());
            skillLimitations.setVisibility(View.VISIBLE);
        } else {
            skillLimitations.setVisibility(View.GONE);
        }

        // Required Tools
        if (skill.getRequiredTools() != null && !skill.getRequiredTools().isEmpty()) {
            skillRequiredTools.setText(skill.getRequiredTools());
            skillRequiredTools.setVisibility(View.VISIBLE);
        } else {
            skillRequiredTools.setVisibility(View.GONE);
        }

        // Tags
        if (skill.getTags() != null && !skill.getTags().isEmpty()) {
            skillTags.setText(skill.getTags());
            skillTags.setVisibility(View.VISIBLE);
        } else {
            skillTags.setVisibility(View.GONE);
        }

        // Author
        if (skill.getAuthor() != null && !skill.getAuthor().isEmpty()) {
            skillAuthor.setText(skill.getAuthor());
            skillAuthor.setVisibility(View.VISIBLE);
        } else {
            skillAuthor.setVisibility(View.GONE);
        }
    }

    static class SkillInfo {
        private String name;
        private String description;
        private String capabilities;
        private String guidelines;
        private String examples;
        private String limitations;
        private String category;
        private String author;
        private String version;
        private String tags;
        private String requiredTools;
        private String content;

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

        public String getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(String capabilities) {
            this.capabilities = capabilities;
        }

        public String getGuidelines() {
            return guidelines;
        }

        public void setGuidelines(String guidelines) {
            this.guidelines = guidelines;
        }

        public String getExamples() {
            return examples;
        }

        public void setExamples(String examples) {
            this.examples = examples;
        }

        public String getLimitations() {
            return limitations;
        }

        public void setLimitations(String limitations) {
            this.limitations = limitations;
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
    }
}
