# Vendored native sources

## llama.cpp

| Item | Value |
| --- | --- |
| Upstream | https://github.com/ggml-org/llama.cpp |
| Release tag | `v0.5.0` (annotated tag object `c13fcbf684171d5e0bca3fc5c34be6a99174b05f`) |
| Commit | `7fe450e19305b828c199d602c23a8337aaa1f03b` |
| Source archive | https://github.com/ggml-org/llama.cpp/archive/refs/tags/v0.5.0.tar.gz |
| Archive SHA-256 | `fef9ed754f4e031fb5c663c29260feda4ebc241abb68d64a81c0f1df5f1748e2` |
| Licence | MIT, copyright The ggml authors; see `llama.cpp/LICENSE` |

The archive records the commit above in its header, and `git ls-remote` resolves the tag to the same commit.

### What is included

`llama.cpp/` holds these paths from the archive, unmodified and byte for byte:
`CMakeLists.txt`, `LICENSE`, `cmake/`, `ggml/`, `include/`, `licenses/`, `src/` and `vendor/`.

Everything else in the archive is left out because the build never uses it: `common/`, `tools/`, `examples/`,
`tests/`, `app/`, `models/`, `docs/`, `gguf-py/`, `scripts/` and the other top-level files. The options in
`../CMakeLists.txt` switch off every target that would need them.

Third-party licences that ship inside the included tree: `licenses/LICENSE-jsonhpp`, `vendor/cpp-httplib/LICENSE`,
`vendor/hash/rotate-bits/LICENSE.md`, `vendor/hash/sha256/LICENSE` and `vendor/hash/xxhash/LICENSE`.

### Checking the vendored copy

1. Download the source archive and confirm that its SHA-256 matches the value above.
2. Extract it and compare each included path with the copy here, for example
   `diff -r <extracted>/ggml llama.cpp/ggml`, and the same for the other paths listed above.
3. Any difference means the vendored tree was edited or the archive does not match.

### Updating

Replace the included paths with those of the new release, update this file with the new tag, commit and
SHA-256, and update `LLAMA_BUILD_COMMIT` in `../CMakeLists.txt`.
