import { useEffect, useMemo, useRef, useState } from "react";
import {
  audioUrl,
  cardUrl,
  cards,
  gestures as allGestures,
  groups,
  languages,
  meaning,
  phrases,
  questions,
  sourceClip,
  uiTexts,
  type Text,
} from "./content";
import { AdultLook, ChildLook, drawCharacter } from "./draw";
import { flags } from "./flags";
import { isMissing, reviewerLang } from "./missing";
import { keyOf, type Review, type Section, type Verdict } from "./reviews";
import { AdultBuild, ChildBuild, Timeline, type Gesture } from "./rig";

export interface ReviewProps {
  role: string;
  reviews: Record<string, Review>;
  onChange: (r: Review) => void;
}

const VERDICTS: [Verdict, string][] = [
  ["A", "Approve"],
  ["C", "Change"],
  ["R", "Reject"],
];

function ReviewBox({
  section,
  id,
  role,
  reviews,
  onChange,
  missing = false,
}: ReviewProps & { section: Section; id: string; missing?: boolean }) {
  const r = reviews[keyOf({ section, id, role })] ?? { section, id, role, verdict: null, notes: "" };
  return (
    <div className="review">
      {VERDICTS.map(([v, label]) => (
        <button
          key={v}
          // Text that does not exist cannot be approved; ask for it with Change and a note.
          disabled={missing && v === "A"}
          className={r.verdict === v ? `on v${v}` : ""}
          aria-pressed={r.verdict === v}
          onClick={() => onChange({ ...r, verdict: r.verdict === v ? null : v })}
        >
          {label}
        </button>
      ))}
      <textarea
        aria-label={`${section} ${id} notes`}
        placeholder={r.verdict === "C" ? "Say what should change" : "Notes"}
        value={r.notes}
        onChange={(e) => onChange({ ...r, notes: e.target.value })}
      />
    </div>
  );
}

const langName = (code: string) => (code === "en" ? "English" : (languages.find((l) => l.code === code)?.english ?? code));

function Character({ gesture, child, seconds }: { gesture: Gesture; child: boolean; seconds: number }) {
  const ref = useRef<HTMLCanvasElement>(null);
  const timeline = useMemo(() => new Timeline(gesture, child ? ChildBuild : AdultBuild), [gesture, child]);
  useEffect(() => {
    const ctx = ref.current?.getContext("2d");
    if (ctx) drawCharacter(ctx, child ? ChildBuild : AdultBuild, child ? ChildLook : AdultLook, timeline.pose(seconds), child);
  }, [timeline, seconds, child]);
  return (
    <figure>
      <canvas ref={ref} width={360} height={360} />
      <figcaption>{child ? "Child" : "Adult"}</figcaption>
    </figure>
  );
}

function Player({ gesture, start }: { gesture: Gesture; start?: number }) {
  const duration = useMemo(() => new Timeline(gesture, ChildBuild).duration, [gesture]);
  const [seconds, setSeconds] = useState(start ?? 0);
  const [playing, setPlaying] = useState(start === undefined);
  const [speed, setSpeed] = useState(0.5);

  useEffect(() => {
    if (!playing) return;
    let frame = 0;
    let last = performance.now();
    const tick = (now: number) => {
      const dt = ((now - last) / 1000) * speed;
      last = now;
      setSeconds((s) => {
        if (s + dt < duration) return s + dt;
        setPlaying(false);
        return duration;
      });
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [playing, speed, duration]);

  return (
    <div>
      <div className="stage">
        <Character gesture={gesture} child seconds={seconds} />
        <Character gesture={gesture} child={false} seconds={seconds} />
      </div>
      <div className="controls">
        <button
          onClick={() => {
            if (seconds >= duration) setSeconds(0);
            setPlaying(!playing);
          }}
        >
          {playing ? "Pause" : "Play"}
        </button>
        <input
          type="range"
          aria-label="Time"
          min={0}
          max={duration}
          step={0.01}
          value={seconds}
          onChange={(e) => {
            setPlaying(false);
            setSeconds(Number(e.target.value));
          }}
        />
        <span className="time">
          {seconds.toFixed(2)} / {duration.toFixed(2)} s
        </span>
        <select aria-label="Speed" value={speed} onChange={(e) => setSpeed(Number(e.target.value))}>
          {[0.25, 0.5, 0.75, 1].map((s) => (
            <option key={s} value={s}>
              {s}x
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

export function IslReview(props: ReviewProps & { items?: Gesture[] }) {
  const gestures = props.items ?? allGestures;
  const [filter, setFilter] = useState("");
  // A link ending in #<gesture id> opens that gesture, and #<gesture id>@<seconds> holds it at that moment.
  const [linkId, linkAt] = decodeURIComponent(location.hash.slice(1)).split("@");
  const [selected, setSelected] = useState(gestures.find((g) => g.id === linkId)?.id ?? gestures[0].id);
  const shown = gestures.filter((g) => `${g.id} ${meaning(g.id).label}`.toLowerCase().includes(filter.toLowerCase()));
  const gesture = gestures.find((g) => g.id === selected)!;
  const m = meaning(selected);
  return (
    <div className="split">
      <nav>
        <input placeholder="Filter" value={filter} onChange={(e) => setFilter(e.target.value)} />
        <ul>
          {shown.map((g) => {
            const v = props.reviews[keyOf({ section: "gesture", id: g.id, role: props.role })]?.verdict;
            return (
              <li key={g.id}>
                <button className={g.id === selected ? "on" : ""} onClick={() => setSelected(g.id)}>
                  <code>{g.id}</code> {meaning(g.id).label}
                  {v && <span className={`badge v${v}`}>{v}</span>}
                </button>
              </li>
            );
          })}
        </ul>
      </nav>
      <section>
        <h2>
          <code>{selected}</code> {m.label}
        </h2>
        {m.how && <p>{m.how}</p>}
        {sourceClip(selected) && (
          <p>
            <a href={sourceClip(selected)} target="_blank" rel="noreferrer">
              Watch the original ISLRTC clip
            </a>
          </p>
        )}
        <Player key={selected} gesture={gesture} start={selected === linkId && linkAt ? Number(linkAt) : undefined} />
        <ReviewBox {...props} section="gesture" id={selected} />
      </section>
    </div>
  );
}

function Captions({ text, audio, lang }: { text: Text; audio?: (code: string) => string | undefined; lang?: string }) {
  return (
    <dl>
      {lang && isMissing(text, lang) && (
        <div>
          <dt>{langName(lang)}</dt>
          <dd className="missing">missing</dd>
        </div>
      )}
      {Object.entries(text).map(([code, t]) => (
        <div key={code}>
          <dt>{langName(code)}</dt>
          <dd lang={code}>
            {t}
            {audio && (audio(code) ? <audio controls preload="none" src={audio(code)} /> : <span className="muted"> (no audio)</span>)}
          </dd>
        </div>
      ))}
    </dl>
  );
}

export function LanguageReview(props: ReviewProps) {
  const lang = reviewerLang(props.role, languages);
  const slots = phrases.flatMap((p) => p.slots);
  const missing = lang
    ? [...questions.map((q) => q.text), ...slots.map((s) => s.text as Text), ...Object.values(uiTexts)].filter((t) => isMissing(t, lang))
        .length
    : 0;
  return (
    <div className="list">
      {lang && (
        <p className={missing ? "note missing" : "note"}>
          {missing} of {questions.length + slots.length + Object.keys(uiTexts).length} texts have no {langName(lang)} caption yet.
        </p>
      )}
      <h2>Prepared questions</h2>
      {questions.map((q) => (
        <article key={q.id}>
          <h3>{q.id}</h3>
          <Captions text={q.text} audio={(c) => audioUrl(c, q.id)} lang={lang} />
          <ReviewBox {...props} section="question" id={q.id} missing={isMissing(q.text, lang)} />
        </article>
      ))}
      <h2>Phrase-bank sentences</h2>
      {phrases.flatMap((p) =>
        p.slots.map((s) => (
          <article key={`${p.id}.${s.card}`}>
            <h3>
              {p.id}.{s.card}
            </h3>
            <Captions text={s.text} audio={(c) => audioUrl(c, `phrase.${p.id}.${s.card}`)} lang={lang} />
            <ReviewBox {...props} section="phrase" id={`${p.id}.${s.card}`} missing={isMissing(s.text as Text, lang)} />
          </article>
        )),
      )}
      <h2>Subject screen words</h2>
      {Object.entries(uiTexts).map(([key, text]) => (
        <article key={key}>
          <h3>{key}</h3>
          <Captions text={text} audio={(c) => audioUrl(c, key)} lang={lang} />
          <ReviewBox {...props} section="ui" id={key} missing={isMissing(text, lang)} />
        </article>
      ))}
      <h2>Language-finder greetings</h2>
      {languages.map((l) => (
        <article key={l.code}>
          <h3>
            {l.english} ({l.code})
          </h3>
          <Captions text={{ [l.code]: l.greeting }} audio={() => audioUrl("greet", l.code)} />
          <ReviewBox {...props} section="greeting" id={l.code} />
        </article>
      ))}
    </div>
  );
}

function Cards({ ids }: { ids: string[] }) {
  return (
    <div className="cards">
      {ids.map((id) => (
        <figure key={id}>
          {cardUrl(id) && <img src={cardUrl(id)} alt="" width={96} height={96} />}
          <figcaption>{cards.get(id)?.label.en ?? id}</figcaption>
        </figure>
      ))}
    </div>
  );
}

function Flags({ list }: { list: string[] }) {
  if (list.length === 0) return null;
  return (
    <ul className="flags">
      {list.map((f) => (
        <li key={f}>{f}</li>
      ))}
    </ul>
  );
}

const categories = (ids: string[]) => ids.map((id) => cards.get(id)?.category ?? "");

export function WelfareReview(props: ReviewProps) {
  return (
    <div className="list">
      <p className="note">
        The hints below come from the avoid list in the research notes. They point at wording to check; they are not a safety check, and a
        question without hints still needs reading.
      </p>
      {groups.map((g) => (
        <div key={g.id}>
          <h2>
            {g.label.en}
            {g.sensitive && " (sensitive)"}
            {g.roles && ` (${g.roles.join(", ").toLowerCase()} only)`}
          </h2>
          {questions
            .filter((q) => q.group === g.id)
            .map((q) => (
              <article key={q.id}>
                <h3>
                  {q.id}
                  {q.gesture && <code> {q.gesture}</code>}
                </h3>
                <Captions text={q.text} />
                <Cards ids={q.cards} />
                <Flags
                  list={flags({ text: q.text.en ?? "", cardCategories: categories(q.cards), gesture: q.gesture, sensitive: !!g.sensitive })}
                />
                <ReviewBox {...props} section="question" id={q.id} />
              </article>
            ))}
        </div>
      ))}
      <h2>Phrase-bank sentences</h2>
      {phrases.flatMap((p) =>
        p.slots.map((s) => (
          <article key={`${p.id}.${s.card}`}>
            <h3>
              {p.id}.{s.card}
            </h3>
            <Captions text={s.text} />
            <Cards ids={[s.card]} />
            <Flags list={flags({ text: s.text.en ?? "", cardCategories: categories([s.card]), sensitive: false })} />
            <ReviewBox {...props} section="phrase" id={`${p.id}.${s.card}`} />
          </article>
        )),
      )}
    </div>
  );
}
