import ReactDOM from 'react-dom/client';

// 定义一个简单的 TSX 组件
const App = () => {
  return <h1>Hello World</h1>;
};

// 将组件渲染到 body 中
const root = ReactDOM.createRoot(document.body);
root.render(<App />);
