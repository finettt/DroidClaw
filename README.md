# 🦞 DroidClaw — Mobile AI Assistant

<div align="center">
	<img src="assets/icon.svg" width="200" height="200" style="border-radius: 15%" />
	<div>
		<h4>
			<strong>
			Your agent needs to be yours.
			</strong>
		</h4>
	</div>
	<a href="https://github.com/finettt/DroidClaw/actions/workflows/android.yml?branch=main"><img src="https://img.shields.io/github/actions/workflow/status/finettt/DroidClaw/android.yml?branch=main&style=for-the-badge" alt="CI status"></a>
	<a href="https://github.com/finettt/DroidClaw/releases"><img src="https://img.shields.io/github/v/release/finettt/DroidClaw?include_prereleases&style=for-the-badge" alt="GitHub release"></a>
	<p>
		<a href="README.ru.md" style="font-size: 1.1em;">🇷🇺 Русский</a>
	</p>
</div>

**DroidClaw** is a personal AI assistant that runs on your phone **without root**, as a native Android/Java app. You don't need to download enormous Node.js builds. ~40MB is all you need (even with an embedded Python interpreter).

DroidClaw is designed to be more than just an assistant; it is designed to be a work partner.

You can connect any provider/model to the app. It supports both the OpenAI and Anthropic APIs, so you can easily start working with your favorite model. We support: OpenAI, Anthropic, OpenRouter, Moonshot, Fireworks, Llama.cpp, vLLM, **and much more!**

# Story

It all starts with an idea. For me, that idea came from wanting to make money. A friend and I wanted to create an all-in-one service with LLMs and image generation models. The biggest advantage of our product was its low price and *file operations*—the agent could create basic text files for you. It could have been a real breakthrough in the agent era. But, due to certain circumstances, the project was closed. About 4 months later, OpenClaw was released. DroidClaw doesn't claim to be an OpenClaw competitor; it is just an educational project.

# Installation

You can download the latest stable version from the [releases page](https://github.com/finettt/DroidClaw/releases/latest) or you can clone and build it manually (see the developer guide).

# Setup (TL;DR)

Go through the onboarding screen, and you are ready to go! If you want to set up things in more detail, see `docs/settings.md`.

# Highlights

- **Bundled Python 3.11:** The agent can execute any scripts to help you.
- **Sandboxed environment:** The agent works in its own filesystem and can't touch your data.
- **Onboarding:** Users can configure the app for its first run with just a couple of questions.

# Docs

All documentation is stored in the `docs` directory.

# FAQ

- Why should **I**, as a user, use your app?
> If you want to try the OpenClaw experience but don't have the hardware (even a Raspberry Pi), you can turn your phone into an agent.

- If I find a **bug**, where can I report it?
> You can create an issue and I'll work on it.

- What do I need as a developer to start working on a feature?
> I recommend using Nix, because it is the simplest way to start developing. Alternatively, you can set up Java 21 and Python 3.11.