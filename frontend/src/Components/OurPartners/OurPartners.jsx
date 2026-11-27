import "./OurPartners.css";

const partners = [
  { name: "M", className: "myntra" },
  { name: "AJIO", className: "ajio" },
  { name: "adidas", className: "adidas" },
  { name: "Flipkart", className: "flipkart" },
  { name: "1+ ONEPLUS", className: "oneplus" },
];

export default function OurPartners() {
  return (
    <section className="partners-section">
      <div className="partners-container">
        <h2>Our Partners</h2>

        <div className="partners-logos">
          {partners.map((partner) => (
            <div
              className={`partner-logo ${partner.className}`}
              key={partner.name}
            >
              {partner.name}
            </div>
          ))}
        </div>

        <p className="partners-more">500+ more</p>
      </div>
    </section>
  );
}
