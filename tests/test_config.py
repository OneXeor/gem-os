from gem_core.config import load_config


def test_load_example_config():
    cfg = load_config(".")

    assert cfg.identity.gem.name == "Gem"
    assert cfg.providers.chat.default == "litellm"
    assert cfg.providers.code_agent.default == "codex"
    assert any(project.id == "aso-fabric" for project in cfg.projects.projects)
