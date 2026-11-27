
import  { useRef } from "react";
import "./SuccessStories.css";

const stories = [
  {
    name: "Saanjh Kanchan",
    role: "Student",
    image: "https://i.pravatar.cc/150?img=47",
    review:
      "I am a college student pursuing a diploma in fashion designing. I love exploring the latest fashion trends and sharing amazing deals from different brands with my friends. Zaylope makes discovering shopping deals simple and convenient.",
  },
  {
    name: "Nidhi Tondon",
    role: "Housewife",
    image: "https://i.pravatar.cc/150?img=44",
    review:
      "I enjoy exploring fashion and beauty deals. I share interesting offers with my relatives and friends through WhatsApp. Zaylope is easy to use and helps me discover exciting shopping offers.",
  },
  {
    name: "Tanmoy",
    role: "Affiliate Partner",
    image: "https://i.pravatar.cc/150?img=12",
    review:
      "I enjoy using Zaylope to explore shopping deals and convert eligible product links into earning links. The simple link conversion process makes sharing offers convenient.",
  },
  {
    name: "Priya Reddy",
    role: "Online Shopper",
    image: "https://i.pravatar.cc/150?img=49",
    review:
      "I love exploring exciting shopping deals and sharing them with my friends and family. Zaylope makes it easy to discover offers from different brands and share useful product links.",
  },
];

export default function SuccessStories() {
  const sliderRef = useRef(null);

  const slide = (direction) => {
    if (!sliderRef.current) return;

    const slider = sliderRef.current;
    const card = slider.querySelector(".success-card");

    if (!card) return;

    const gap = 14;
    const scrollAmount = card.getBoundingClientRect().width + gap;

    slider.scrollBy({
      left: direction * scrollAmount,
      behavior: "smooth",
    });
  };

  return (
    <section className="success-section">
      <div className="success-container">

        <h2>Our success story</h2>

        <div className="success-carousel">

          {/* Backward Button */}
          <button
            className="success-arrow success-left"
            onClick={() => slide(-1)}
            aria-label="Previous testimonial"
            type="button"
          >
            ←
          </button>

          {/* Testimonial Cards */}
          <div className="success-slider" ref={sliderRef}>

            {stories.map((story, index) => (
              <div className="success-card" key={index}>

                <div className="success-card-header">

                  <img
                    src={story.image}
                    alt={story.name}
                    className="success-avatar"
                  />

                  <div className="success-user-info">
                    <h3>{story.name}</h3>

                    <div
                      className="success-stars"
                      aria-label="5 out of 5 stars"
                    >
                      ★★★★★
                    </div>
                  </div>

                </div>

                <div className="success-divider"></div>

                <div className="success-review">

                  <p>“{story.review}”</p>

                  <span>-- {story.role}</span>

                </div>

              </div>
            ))}

          </div>

          {/* Forward Button */}
          <button
            className="success-arrow success-right"
            onClick={() => slide(1)}
            aria-label="Next testimonial"
            type="button"
          >
            →
          </button>

        </div>
      </div>
    </section>
  );
}
