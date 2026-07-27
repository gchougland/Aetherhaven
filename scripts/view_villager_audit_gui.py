"""GUI viewer for Aetherhaven villager audit JSONL logs."""
from __future__ import annotations

import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from pathlib import Path

from villager_audit_lib import (
    DEFAULT_AUDIT_ROOT,
    discover_audit_files,
    event_label,
    filter_rows,
    format_details,
    format_time,
    load_from_paths,
    source_label,
    summary_text,
)

DARK_THEME = {
    "bg": "#1a1a1a",
    "surface": "#242424",
    "surface_alt": "#2d2d2d",
    "header": "#333333",
    "border": "#3a3a3a",
    "fg": "#e8e8e8",
    "muted": "#a8a8a8",
    "accent": "#5aa9ff",
    "select": "#2d5a87",
    "select_fg": "#ffffff",
    "death": "#ff7070",
    "removed": "#ffb454",
    "missing": "#c9a6ff",
    "insert": "#e8e8e8",
}


class VillagerAuditViewer(tk.Tk):
    def __init__(self, initial_path: Path | None = None) -> None:
        super().__init__()
        self.title("Aetherhaven Villager Audit Log")
        self.geometry("1100x700")
        self.minsize(900, 520)

        self.all_rows: list[dict] = []
        self.filtered_rows: list[dict] = []
        self.loaded_files: list[Path] = []
        self.row_by_iid: dict[str, dict] = {}

        self._apply_dark_theme()
        self._build_ui()
        self._bind_events()

        if initial_path and initial_path.is_file():
            self.load_files([initial_path])
        else:
            defaults = discover_audit_files()
            if defaults:
                self.load_files(defaults)
            else:
                self.status_var.set("Open an audit.jsonl file or audit folder to begin.")

    def _apply_dark_theme(self) -> None:
        c = DARK_THEME
        self.configure(bg=c["bg"])

        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass

        style.configure(".", background=c["bg"], foreground=c["fg"], bordercolor=c["border"])
        style.configure("TFrame", background=c["bg"])
        style.configure("TLabel", background=c["bg"], foreground=c["fg"])
        style.configure(
            "TButton",
            background=c["surface_alt"],
            foreground=c["fg"],
            bordercolor=c["border"],
            focusthickness=0,
            padding=(10, 6),
        )
        style.map(
            "TButton",
            background=[("active", c["header"]), ("pressed", c["select"])],
            foreground=[("disabled", c["muted"])],
        )
        style.configure(
            "TEntry",
            fieldbackground=c["surface"],
            foreground=c["fg"],
            insertcolor=c["insert"],
            bordercolor=c["border"],
        )
        style.configure(
            "TCombobox",
            fieldbackground=c["surface"],
            background=c["surface_alt"],
            foreground=c["fg"],
            arrowcolor=c["fg"],
            bordercolor=c["border"],
        )
        style.map(
            "TCombobox",
            fieldbackground=[("readonly", c["surface"])],
            foreground=[("readonly", c["fg"])],
        )
        style.configure(
            "TLabelframe",
            background=c["bg"],
            foreground=c["muted"],
            bordercolor=c["border"],
        )
        style.configure("TLabelframe.Label", background=c["bg"], foreground=c["muted"])
        style.configure("TPanedwindow", background=c["bg"])
        style.configure("Vertical.TScrollbar", background=c["surface_alt"], troughcolor=c["bg"], bordercolor=c["border"])
        style.map("Vertical.TScrollbar", background=[("active", c["header"])])
        style.configure(
            "Treeview",
            background=c["surface"],
            foreground=c["fg"],
            fieldbackground=c["surface"],
            bordercolor=c["border"],
            rowheight=26,
        )
        style.configure(
            "Treeview.Heading",
            background=c["header"],
            foreground=c["fg"],
            relief="flat",
            bordercolor=c["border"],
        )
        style.map(
            "Treeview",
            background=[("selected", c["select"])],
            foreground=[("selected", c["select_fg"])],
        )
        style.map(
            "Treeview.Heading",
            background=[("active", c["surface_alt"])],
        )

    def _build_ui(self) -> None:
        c = DARK_THEME
        toolbar = ttk.Frame(self, padding=(8, 8, 8, 4))
        toolbar.pack(fill=tk.X)

        ttk.Button(toolbar, text="Open File…", command=self.open_file).pack(side=tk.LEFT)
        ttk.Button(toolbar, text="Open Folder…", command=self.open_folder).pack(side=tk.LEFT, padx=(6, 0))
        ttk.Button(toolbar, text="Reload", command=self.reload_files).pack(side=tk.LEFT, padx=(6, 0))

        ttk.Label(toolbar, text="Event").pack(side=tk.LEFT, padx=(16, 4))
        self.event_var = tk.StringVar(value="All")
        self.event_combo = ttk.Combobox(
            toolbar,
            textvariable=self.event_var,
            values=["All", "DEATH", "REMOVED", "DETECTED_MISSING"],
            width=16,
            state="readonly",
        )
        self.event_combo.pack(side=tk.LEFT)

        ttk.Label(toolbar, text="Search").pack(side=tk.LEFT, padx=(12, 4))
        self.search_var = tk.StringVar()
        search_entry = ttk.Entry(toolbar, textvariable=self.search_var, width=28)
        search_entry.pack(side=tk.LEFT, fill=tk.X, expand=True)

        path_frame = ttk.Frame(self, padding=(8, 0, 8, 4))
        path_frame.pack(fill=tk.X)
        ttk.Label(path_frame, text="Loaded:").pack(side=tk.LEFT)
        self.path_var = tk.StringVar(value="(none)")
        ttk.Label(path_frame, textvariable=self.path_var).pack(side=tk.LEFT, fill=tk.X, expand=True)

        paned = ttk.Panedwindow(self, orient=tk.VERTICAL)
        paned.pack(fill=tk.BOTH, expand=True, padx=8, pady=4)

        table_frame = ttk.Frame(paned)
        paned.add(table_frame, weight=3)

        columns = ("time", "event", "name", "town", "reason")
        self.tree = ttk.Treeview(
            table_frame,
            columns=columns,
            show="headings",
            selectmode="browse",
        )
        self.tree.heading("time", text="Time (UTC)")
        self.tree.heading("event", text="Event")
        self.tree.heading("name", text="Name")
        self.tree.heading("town", text="Town")
        self.tree.heading("reason", text="Reason")

        self.tree.column("time", width=150, minwidth=130, stretch=False)
        self.tree.column("event", width=80, minwidth=70, stretch=False)
        self.tree.column("name", width=180, minwidth=120)
        self.tree.column("town", width=120, minwidth=90)
        self.tree.column("reason", width=360, minwidth=200)

        yscroll = ttk.Scrollbar(table_frame, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=yscroll.set)
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        yscroll.pack(side=tk.RIGHT, fill=tk.Y)

        self.tree.tag_configure("death", foreground=c["death"])
        self.tree.tag_configure("removed", foreground=c["removed"])
        self.tree.tag_configure("missing", foreground=c["missing"])

        detail_frame = ttk.LabelFrame(paned, text="Details", padding=8)
        paned.add(detail_frame, weight=1)

        self.detail_text = tk.Text(
            detail_frame,
            height=8,
            wrap=tk.WORD,
            font=("Consolas", 10),
            relief=tk.FLAT,
            padx=8,
            pady=8,
            bg=c["surface"],
            fg=c["fg"],
            insertbackground=c["insert"],
            selectbackground=c["select"],
            selectforeground=c["select_fg"],
            highlightthickness=1,
            highlightbackground=c["border"],
            highlightcolor=c["accent"],
        )
        self.detail_text.pack(fill=tk.BOTH, expand=True)
        self.detail_text.configure(state=tk.DISABLED)

        status_frame = ttk.Frame(self, padding=(8, 4, 8, 8))
        status_frame.pack(fill=tk.X)
        self.status_var = tk.StringVar(value="Ready")
        ttk.Label(status_frame, textvariable=self.status_var).pack(side=tk.LEFT)

    def _bind_events(self) -> None:
        self.event_combo.bind("<<ComboboxSelected>>", lambda _e: self.apply_filters())
        self.search_var.trace_add("write", lambda *_: self.apply_filters())
        self.tree.bind("<<TreeviewSelect>>", self.on_select)

    def open_file(self) -> None:
        path = filedialog.askopenfilename(
            title="Open audit.jsonl",
            filetypes=[("Audit log", "audit.jsonl"), ("JSONL", "*.jsonl"), ("All files", "*.*")],
            initialdir=str(DEFAULT_AUDIT_ROOT if DEFAULT_AUDIT_ROOT.exists() else Path.home()),
        )
        if path:
            self.load_files([Path(path)])

    def open_folder(self) -> None:
        path = filedialog.askdirectory(
            title="Open audit folder",
            initialdir=str(DEFAULT_AUDIT_ROOT if DEFAULT_AUDIT_ROOT.exists() else Path.home()),
        )
        if path:
            files = sorted(Path(path).rglob("audit.jsonl"))
            if not files:
                messagebox.showinfo("No logs", "No audit.jsonl files found in that folder.")
                return
            self.load_files(files)

    def reload_files(self) -> None:
        if not self.loaded_files:
            self.open_file()
            return
        self.load_files(self.loaded_files)

    def load_files(self, paths: list[Path]) -> None:
        rows, warnings = load_from_paths(paths)
        self.all_rows = rows
        self.loaded_files = list(paths)
        if len(paths) == 1:
            self.path_var.set(str(paths[0]))
        else:
            self.path_var.set(f"{len(paths)} file(s) under {paths[0].parent}")
        self.apply_filters()
        if warnings:
            messagebox.showwarning("Load warnings", "\n".join(warnings[:12]))

    def apply_filters(self) -> None:
        self.filtered_rows = filter_rows(
            self.all_rows,
            event=self.event_var.get(),
            search=self.search_var.get().strip() or None,
        )
        self.refresh_table()

    def refresh_table(self) -> None:
        self.tree.delete(*self.tree.get_children())
        self.row_by_iid.clear()
        self.set_details(None)

        for index, row in enumerate(self.filtered_rows):
            event = str(row.get("event", ""))
            tag = ""
            upper = event.upper()
            if upper == "DEATH":
                tag = "death"
            elif upper == "REMOVED":
                tag = "removed"
            elif upper == "DETECTED_MISSING":
                tag = "missing"

            iid = str(index)
            self.row_by_iid[iid] = row
            self.tree.insert(
                "",
                tk.END,
                iid=iid,
                values=(
                    format_time(row.get("epochMs")),
                    event_label(event),
                    row.get("displayName") or "?",
                    row.get("townName") or row.get("townId") or "?",
                    source_label(str(row.get("source", ""))),
                ),
                tags=(tag,) if tag else (),
            )

        self.status_var.set(summary_text(self.filtered_rows))

    def on_select(self, _event: object = None) -> None:
        selected = self.tree.selection()
        if not selected:
            self.set_details(None)
            return
        row = self.row_by_iid.get(selected[0])
        self.set_details(row)

    def set_details(self, row: dict | None) -> None:
        self.detail_text.configure(state=tk.NORMAL)
        self.detail_text.delete("1.0", tk.END)
        if row is not None:
            self.detail_text.insert("1.0", format_details(row))
        self.detail_text.configure(state=tk.DISABLED)


def main() -> None:
    import sys

    initial: Path | None = None
    if len(sys.argv) > 1:
        initial = Path(sys.argv[1])
    app = VillagerAuditViewer(initial)
    app.mainloop()


if __name__ == "__main__":
    main()
