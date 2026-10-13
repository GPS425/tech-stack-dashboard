function Footer({ source, copyright, onHomeAction }) {
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <a
          className="brand footer-brand"
          href="./"
          onClick={(event) => {
            if (onHomeAction) { event.preventDefault(); onHomeAction(); }
          }}
        >JOBSTACK</a>
        <div className="footer-information">
          <p>데이터 출처: {source}</p>
          <p>{copyright}</p>
        </div>
      </div>
    </footer>
  );
}

export default Footer;
