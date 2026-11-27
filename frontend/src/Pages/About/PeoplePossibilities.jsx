import graduationCap from '../../assets/audience-icons/graduation-cap.svg';
import briefcase from '../../assets/audience-icons/briefcase-business.svg';
import clapperboard from '../../assets/audience-icons/clapperboard.svg';
import house from '../../assets/audience-icons/house.svg';
import store from '../../assets/audience-icons/store.svg';
import users from '../../assets/audience-icons/users-round.svg';
import './PeoplePossibilities.css';

// Official Lucide SVGs, ISC licensed. Notice: public/licenses/lucide.txt.
const audiences = [
  ['Students', 'Share useful discoveries with your circles.', graduationCap],
  ['Working professionals', 'Recommend finds that fit everyday life.', briefcase],
  ['Content creators', 'Connect product stories with your audience.', clapperboard],
  ['Homemakers', 'Bring practical finds to your community.', house],
  ['Entrepreneurs', 'Explore another way to connect through commerce.', store],
  ['Community builders', 'Start conversations around shared interests.', users],
];

export default function PeoplePossibilities() {
  return <section className="people-band" id="people-possibilities" aria-labelledby="about-audience-title">
    <div className="about-container people-section" data-about-reveal>
      <header className="people-heading">
        <p className="about-eyebrow">05 / People &amp; Possibilities</p>
        <h2 id="about-audience-title">Different lives.<br/><span>A shared curiosity.</span></h2>
        <p>For people who enjoy discovering something useful and passing it on.</p>
      </header>
      <ul className="people-grid" aria-label="Potential audiences">
        {audiences.map(([title,description,icon],index)=><li className="people-item" key={title} style={{'--people-index':index}}>
          <article className="people-card">
            <span className="people-icon" aria-hidden="true"><span style={{maskImage:`url("${icon}")`,WebkitMaskImage:`url("${icon}")`}}/></span>
            <h3>{title}</h3><p>{description}</p>
          </article>
        </li>)}
      </ul>
      <div className="people-note about-supporting-note"><p>These are potential audiences, not guaranteed membership categories.</p><p>Participation is subject to membership eligibility and applicable programme rules.</p></div>
    </div>
  </section>;
}
